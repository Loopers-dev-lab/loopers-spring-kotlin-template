package com.loopers.interfaces.scheduler

import com.loopers.domain.ranking.RankingAggregationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["scheduler.ranking-score.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class RankingScoreScheduler(
    private val rankingAggregationService: RankingAggregationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 1800000) // 30 minutes
    fun calculateScores() {
        log.debug("[RankingScoreScheduler] triggering score calculation")
        rankingAggregationService.calculateAndUpdateScores()
    }
}
