package com.loopers.domain.ranking

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * RankingAggregationService - 랭킹 집계 핵심 서비스
 *
 * - 서비스가 버퍼를 내부적으로 소유 (스케줄러는 순수 트리거)
 * - AtomicReference로 버퍼 스왑을 원자적으로 수행
 * - ConcurrentHashMap의 copy + clear 방식은 그 사이에 도착한 이벤트를 잃을 위험이 있음
 *   getAndSet으로 전체 버퍼를 원자적으로 교체하면 이후 add()는 새 버퍼에 기록됨
 */
@Service
class RankingAggregationService(
    private val metricRepository: ProductHourlyMetricRepository,
    private val rankingWriter: ProductRankingWriter,
    private val rankingWeightRepository: RankingWeightRepository,
    private val scoreCalculator: RankingScoreCalculator,
) {
    companion object {
        private val TTL_SECONDS = Duration.ofHours(25).seconds
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    // 버퍼는 서비스 내부에서 소유
    private val bufferRef = AtomicReference(ConcurrentHashMap<AggregationKey, MutableCounts>())

    /**
     * 이벤트를 내부 버퍼에 추가
     *
     * 시간 버킷은 이벤트의 occurredAt을 시간 단위로 truncate하여 결정
     * 14:59:58에 발생한 이벤트가 15:00:02에 flush되어도 14:00 버킷에 올바르게 집계됨
     */
    fun add(event: RankingEvent) {
        val key = AggregationKey.from(event)

        bufferRef.get()
            .computeIfAbsent(key) { MutableCounts() }
            .apply {
                increment(event.eventType)
                event.orderAmount?.let { addOrderAmount(it) }
            }
    }

    /**
     * 버퍼를 원자적으로 교체하고 이전 버퍼의 데이터를 영속화
     *
     * 스케줄러에서 순수 트리거로 호출됨
     */
    fun flush() {
        val data = bufferRef.getAndSet(ConcurrentHashMap())
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

        // Redis: Pipeline으로 점수 증분 업데이트 (네트워크 왕복 최소화)
        // ZINCRBY는 기존 점수에 더하므로 여러 번 호출해도 누적
        rankingWriter.incrementScores(redisKey, scoreDeltas)
        rankingWriter.setTtl(redisKey, TTL_SECONDS)

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
}
