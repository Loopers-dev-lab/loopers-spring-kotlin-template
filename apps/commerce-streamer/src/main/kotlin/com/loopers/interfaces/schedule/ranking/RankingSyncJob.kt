package com.loopers.interfaces.schedule.ranking

import com.loopers.application.productMetric.ProductMetricFacade
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RankingSyncJob(
    private val productMetricFacade: ProductMetricFacade,
) {

    @Scheduled(cron = "0 0 * * * *")
    fun syncRanking() {
        productMetricFacade.updateRanking()
    }
}
