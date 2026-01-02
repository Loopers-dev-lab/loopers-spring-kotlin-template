package com.loopers.domain.ranking

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.temporal.ChronoUnit

/**
 * RankingAggregationService - 랭킹 집계 핵심 서비스
 *
 * - accumulateMetrics: 배치로 메트릭을 DB에 저장 (Kafka Consumer에서 사용)
 *
 * Note: calculateHourRankings(), rollupHourlyToDaily(), calculateDailyRankings() 메서드는
 * commerce-batch 모듈의 Spring Batch Job들로 마이그레이션되었습니다:
 * - HourlyRankingJob: 시간별 랭킹 계산
 * - TodayDailyRollupJob: 당일 시간별→일별 롤업
 * - YesterdayReconciliationJob: 전일 데이터 재집계
 * - DailyRankingJob: 일별 랭킹 계산
 */
@Service
class RankingAggregationService(
    private val metricRepository: ProductHourlyMetricRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 배치로 메트릭을 DB에 직접 저장
     *
     * - Consumer에서 배치로 호출
     * - ProductHourlyMetricRow로 변환하여 batchAccumulateCounts 호출
     *
     * @param command 배치 메트릭 커맨드
     */
    fun accumulateMetrics(command: AccumulateMetricsCommand) {
        if (command.items.isEmpty()) return

        val rows = command.items.map { item ->
            ProductHourlyMetricRow(
                productId = item.productId,
                statHour = item.statHour.truncatedTo(ChronoUnit.HOURS),
                viewCount = item.viewDelta,
                likeCount = item.likeCreatedDelta - item.likeCanceledDelta,
                orderAmount = item.orderAmountDelta,
            )
        }

        metricRepository.batchAccumulateCounts(rows)
        logger.debug("Accumulated {} metrics to DB", rows.size)
    }
}
