package com.loopers.domain.ranking

import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * RankingAggregationService - 랭킹 집계 핵심 서비스
 *
 * - 서비스가 버퍼를 내부적으로 소유 (스케줄러는 순수 트리거)
 * - MetricBuffer를 통해 원자적 버퍼 스왑 수행
 * - poll() 시 새 버퍼로 교체되어 이벤트 유실 없이 안전하게 drain
 * - 멱등성 체크를 서비스 레이어에서 수행 (EventHandledRepository 사용)
 */
@Service
class RankingAggregationService(
    private val metricRepository: ProductHourlyMetricRepository,
    private val rankingWriter: ProductRankingWriter,
    private val rankingReader: ProductRankingReader,
    private val rankingWeightRepository: RankingWeightRepository,
    private val scoreCalculator: RankingScoreCalculator,
    private val eventHandledRepository: EventHandledRepository,
) {
    companion object {
        private val DECAY_FACTOR = java.math.BigDecimal("0.1")
        private const val MAX_RETRY_COUNT = 3
        private const val IDEMPOTENCY_PREFIX = "ranking"
        private const val BUFFER_HARD_THRESHOLD = 10000
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    // 버퍼는 서비스 내부에서 소유
    private val buffer = MetricBuffer()

    // ==================== Public API with Idempotency ====================

    /**
     * VIEW 이벤트 메트릭을 축적
     *
     * 멱등성 체크 -> 버퍼 축적 -> 멱등성 기록 순서로 수행
     * 기록 실패는 비즈니스 로직에 영향을 주지 않음
     */
    fun accumulateViewMetric(command: AccumulateViewMetricCommand) {
        val idempotencyKey = "$IDEMPOTENCY_PREFIX:view:${command.eventId}"
        if (eventHandledRepository.existsByIdempotencyKey(idempotencyKey)) return

        accumulateToBuffer(AggregationKey.of(command.productId, command.occurredAt)) {
            increment(MetricType.VIEW)
        }

        eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
    }

    /**
     * LIKE_CREATED 이벤트 메트릭을 축적
     */
    fun accumulateLikeCreatedMetric(command: AccumulateLikeCreatedMetricCommand) {
        val idempotencyKey = "$IDEMPOTENCY_PREFIX:like-created:${command.eventId}"
        if (eventHandledRepository.existsByIdempotencyKey(idempotencyKey)) return

        accumulateToBuffer(AggregationKey.of(command.productId, command.occurredAt)) {
            increment(MetricType.LIKE_CREATED)
        }

        eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
    }

    /**
     * LIKE_CANCELED 이벤트 메트릭을 축적
     */
    fun accumulateLikeCanceledMetric(command: AccumulateLikeCanceledMetricCommand) {
        val idempotencyKey = "$IDEMPOTENCY_PREFIX:like-canceled:${command.eventId}"
        if (eventHandledRepository.existsByIdempotencyKey(idempotencyKey)) return

        accumulateToBuffer(AggregationKey.of(command.productId, command.occurredAt)) {
            increment(MetricType.LIKE_CANCELED)
        }

        eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
    }

    /**
     * ORDER_PAID 이벤트 메트릭을 축적
     *
     * 여러 주문 아이템을 각각의 상품별로 버퍼에 축적
     */
    fun accumulateOrderPaidMetric(command: AccumulateOrderPaidMetricCommand) {
        val idempotencyKey = "$IDEMPOTENCY_PREFIX:order-paid:${command.eventId}"
        if (eventHandledRepository.existsByIdempotencyKey(idempotencyKey)) return

        command.items.forEach { item ->
            accumulateToBuffer(AggregationKey.of(item.productId, command.occurredAt)) {
                increment(MetricType.ORDER_PAID)
                addOrderAmount(item.orderAmount)
            }
        }

        eventHandledRepository.save(EventHandled(idempotencyKey = idempotencyKey))
    }

    // ==================== Buffer Management ====================

    /**
     * 모든 버퍼 연산의 단일 진입점
     * threshold 체크가 누락되지 않도록 보장
     */
    private fun accumulateToBuffer(key: AggregationKey, action: MutableCounts.() -> Unit) {
        buffer.accumulate(key, action)
        checkBufferThreshold()
    }

    /**
     * 버퍼 임계치 체크 및 필요시 동기적 flush 수행
     */
    private fun checkBufferThreshold() {
        val currentSize = buffer.size()
        if (currentSize >= BUFFER_HARD_THRESHOLD) {
            logger.warn("Buffer reached threshold ({}), triggering synchronous flush", currentSize)
            flush()
        }
    }

    /**
     * 버퍼를 원자적으로 교체하고 이전 버퍼의 데이터를 영속화
     *
     * 스케줄러에서 순수 트리거로 호출됨
     */
    fun flush() {
        val data = buffer.poll()
        if (data.isEmpty()) return

        try {
            // 시간 버킷별로 그룹화 - 보통 하나지만 시간 경계에서는 두 개가 섞일 수 있음
            data.entries
                .groupBy { it.key.hourBucket }
                .forEach { (hourBucket, entries) ->
                    persistBucket(hourBucket, entries)
                }
            logger.info("Ranking aggregation flushed: {} entries", data.size)
        } catch (e: Exception) {
            logger.error("Ranking aggregation flush failed", e)
        }
    }

    private fun persistBucket(
        hourBucket: java.time.Instant,
        entries: List<Map.Entry<AggregationKey, MutableCounts>>,
    ) {
        val redisKey = RankingKeyGenerator.bucketKey(hourBucket)
        val weights = rankingWeightRepository.findLatest() ?: RankingWeight.fallback()

        // 점수 델타 계산
        val scoreDeltas = entries.associate { (key, counts) ->
            val snapshot = counts.toSnapshot()
            val score = scoreCalculator.calculate(snapshot, weights)
            key.productId to score
        }

        // Redis: Pipeline으로 점수 증분 업데이트
        // ZINCRBY는 기존 점수에 더하므로 여러 번 호출해도 누적
        rankingWriter.incrementScores(redisKey, scoreDeltas)

        // DB: 카운트 누적 (점수는 저장하지 않음 - 가중치 변경 시 재계산 가능하도록)
        // RDB 쓰기는 3회 재시도
        val statsToSave = entries.map { (key, counts) ->
            val snapshot = counts.toSnapshot()
            ProductHourlyMetricRow(
                productId = key.productId,
                statHour = key.hourBucket,
                viewCount = snapshot.views,
                likeCount = snapshot.likes,
                orderCount = snapshot.orderCount,
                orderAmount = snapshot.orderAmount,
            )
        }
        executeWithRetry("batchAccumulateCounts") {
            metricRepository.batchAccumulateCounts(statsToSave)
        }
    }

    /**
     * 시간별 버킷 전환 수행
     *
     * 이전 버킷의 점수에 감쇠 계수(0.1)를 적용하여 새 버킷의 초기 점수로 설정
     *
     * 예: 14시 버킷에 상품1=100점, 상품2=50점이 있다면
     *     15시 버킷은 상품1=10점, 상품2=5점으로 시작
     */
    fun transitionBucket() {
        val previousBucketKey = RankingKeyGenerator.previousBucketKey()
        val currentBucketKey = RankingKeyGenerator.currentBucketKey()

        try {
            // 이전 버킷이 없으면 전환할 것이 없음
            if (!rankingReader.exists(previousBucketKey)) {
                logger.debug("Previous bucket does not exist: {}", previousBucketKey)
                return
            }

            // 현재 버킷이 이미 존재하면 이미 전환됨 (중복 실행 방지)
            if (rankingReader.exists(currentBucketKey)) {
                logger.debug("Current bucket already exists: {}", currentBucketKey)
                return
            }

            // 이전 버킷의 모든 점수 조회
            val previousScores = rankingReader.getAllScores(previousBucketKey)
            if (previousScores.isEmpty()) {
                logger.debug("Previous bucket is empty: {}", previousBucketKey)
                return
            }

            // 감쇠 적용하여 새 버킷 생성
            // Redis createBucket은 3회 재시도
            val decayedScores = previousScores.mapValues { (_, score) ->
                score.applyDecay(DECAY_FACTOR)
            }

            executeWithRetry("createBucket") {
                rankingWriter.createBucket(currentBucketKey, decayedScores)
            }
            logger.info(
                "Bucket transition completed: {} -> {}, {} products with decayed scores",
                previousBucketKey,
                currentBucketKey,
                decayedScores.size,
            )
        } catch (e: Exception) {
            logger.error("Bucket transition failed: {} -> {}", previousBucketKey, currentBucketKey, e)
        }
    }

    /**
     * 재시도 로직 래퍼
     *
     * 실패 시 최대 3회까지 재시도하고, 모든 시도가 실패하면 에러 로깅 후 예외를 던짐
     */
    private fun executeWithRetry(
        operationName: String,
        action: () -> Unit,
    ) {
        var lastException: Exception? = null

        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                action()
                return
            } catch (e: Exception) {
                lastException = e
                logger.warn(
                    "{} failed, retry attempt {} of {}",
                    operationName,
                    attempt + 1,
                    MAX_RETRY_COUNT,
                )
            }
        }

        logger.error("{} failed after {} retry attempts, data may be lost", operationName, MAX_RETRY_COUNT)
        throw lastException!!
    }

    // ==================== Lifecycle ====================

    /**
     * 애플리케이션 종료 시 버퍼에 남은 데이터를 최종 flush
     *
     * SIGTERM 수신 시 Spring 컨테이너가 @PreDestroy 메서드를 호출하여
     * 버퍼 데이터 손실을 방지함
     */
    @PreDestroy
    fun onShutdown() {
        logger.info("Shutting down RankingAggregationService, performing final flush...")
        flush()
        logger.info("Final flush completed")
    }
}
