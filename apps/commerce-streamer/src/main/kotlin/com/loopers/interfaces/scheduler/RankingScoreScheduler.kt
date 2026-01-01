package com.loopers.interfaces.scheduler

import com.loopers.domain.ranking.RankingAggregationService
import com.loopers.domain.ranking.RankingPeriod
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
@ConditionalOnProperty(
    name = ["scheduler.ranking-score.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class RankingScoreScheduler(
    private val rankingAggregationService: RankingAggregationService,
) {
    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
    }

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 1800000) // 30 minutes
    fun calculateScores() {
        log.debug("[RankingScoreScheduler] triggering score calculation")
        val now = ZonedDateTime.now(SEOUL_ZONE)
        rankingAggregationService.calculateAndUpdateScores(RankingPeriod.HOURLY, now)
    }
}
