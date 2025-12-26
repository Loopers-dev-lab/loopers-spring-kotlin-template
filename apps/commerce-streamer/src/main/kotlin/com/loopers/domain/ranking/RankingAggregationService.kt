package com.loopers.domain.ranking

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * RankingAggregationService - 랭킹 집계 핵심 서비스
 *
 * - 서비스가 버퍼를 내부적으로 소유 (스케줄러는 순수 트리거)
 * - MetricBuffer를 통해 원자적 버퍼 스왑 수행
 * - poll() 시 새 버퍼로 교체되어 이벤트 유실 없이 안전하게 drain
 */
@Service
class RankingAggregationService(
    private val metricRepository: ProductHourlyMetricRepository,
    private val rankingWriter: ProductRankingWriter,
    private val rankingReader: ProductRankingReader,
    private val rankingWeightRepository: RankingWeightRepository,
    private val scoreCalculator: RankingScoreCalculator,
) {
    companion object {
        private val DECAY_FACTOR = java.math.BigDecimal("0.1")
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    // 버퍼는 서비스 내부에서 소유
    private val buffer = MetricBuffer()

    /**
     * 메트릭을 내부 버퍼에 축적
     *
     * 시간 버킷은 항목의 occurredAt을 시간 단위로 truncate하여 결정
     * 14:59:58에 발생한 항목이 15:00:02에 flush되어도 14:00 버킷에 올바르게 집계됨
     */
    fun accumulateMetric(command: AccumulateMetricCommand) {
        command.items.forEach { item ->
            val key = AggregationKey.from(item)

            buffer.accumulate(key) {
                increment(item.metricType)
                item.orderAmount?.let { addOrderAmount(it) }
            }
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
        metricRepository.batchAccumulateCounts(statsToSave)
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
            val decayedScores = previousScores.mapValues { (_, score) ->
                score.applyDecay(DECAY_FACTOR)
            }

            rankingWriter.createBucket(currentBucketKey, decayedScores)
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
}
