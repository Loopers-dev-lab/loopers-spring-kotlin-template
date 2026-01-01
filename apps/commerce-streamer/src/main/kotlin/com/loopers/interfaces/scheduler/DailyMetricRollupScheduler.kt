package com.loopers.interfaces.scheduler

import com.loopers.domain.ranking.RankingAggregationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * DailyMetricRollupScheduler - 일별 메트릭 롤업 및 랭킹 계산 스케줄러
 *
 * - 매일 00:00, 12:00 (Asia/Seoul)에 실행
 * - 시간별 메트릭을 일별 메트릭으로 롤업
 * - 일별 랭킹을 계산하여 Redis에 저장
 */
@Component
@ConditionalOnProperty(
    name = ["scheduler.daily-metric-rollup.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class DailyMetricRollupScheduler(
    private val rankingAggregationService: RankingAggregationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 0,12 * * *", zone = "Asia/Seoul")
    fun rollupAndCalculateDaily() {
        log.info("[DailyMetricRollupScheduler] triggering daily rollup and ranking calculation")
        rankingAggregationService.rollupHourlyToDaily()
        rankingAggregationService.calculateDailyRankings()
    }
}
