package com.loopers.batch.listener

import com.loopers.batch.job.ranking.step.ScoreEntry
import com.loopers.domain.ranking.ProductDailyMetric
import org.slf4j.LoggerFactory
import org.springframework.batch.core.SkipListener
import org.springframework.stereotype.Component

@Component
class SkipLoggingListener : SkipListener<ProductDailyMetric, ScoreEntry> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSkipInRead(t: Throwable) {
        log.error("[SKIP-READ] Failed to read item", t)
    }

    override fun onSkipInProcess(item: ProductDailyMetric, t: Throwable) {
        log.error(
            "[SKIP-PROCESS] productId={}, statDate={}, error={}",
            item.productId,
            item.statDate,
            t.message,
            t,
        )
    }

    override fun onSkipInWrite(item: ScoreEntry, t: Throwable) {
        log.error(
            "[SKIP-WRITE] productId={}, score={}, error={}",
            item.productId,
            item.score,
            t.message,
            t,
        )
    }
}
