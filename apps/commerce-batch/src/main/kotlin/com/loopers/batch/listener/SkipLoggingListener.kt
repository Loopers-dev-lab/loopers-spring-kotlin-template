package com.loopers.batch.listener

import org.slf4j.LoggerFactory
import org.springframework.batch.core.SkipListener
import org.springframework.stereotype.Component

/**
 * SkipLoggingListener - 모든 랭킹 Job에서 재사용 가능한 스킵 로깅 리스너
 *
 * Generic 타입 <Any, Any>를 사용하여 다양한 입력/출력 타입을 처리:
 * - ProductDailyMetric -> ScoreEntry (Weekly, Monthly)
 * - ProductHourlyMetric -> ScoreEntry (Hourly)
 * - ProductDailyMetric -> ProductDailyMetric (Rollup)
 */
@Component
class SkipLoggingListener : SkipListener<Any, Any> {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun onSkipInRead(t: Throwable) {
        log.error("[SKIP-READ] Failed to read item", t)
    }

    override fun onSkipInProcess(item: Any, t: Throwable) {
        log.error(
            "[SKIP-PROCESS] item={}, error={}",
            item,
            t.message,
            t,
        )
    }

    override fun onSkipInWrite(item: Any, t: Throwable) {
        log.error(
            "[SKIP-WRITE] item={}, error={}",
            item,
            t.message,
            t,
        )
    }
}
