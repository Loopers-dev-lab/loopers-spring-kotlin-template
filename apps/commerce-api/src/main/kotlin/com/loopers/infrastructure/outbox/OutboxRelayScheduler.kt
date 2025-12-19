package com.loopers.infrastructure.outbox

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * OutboxRelayScheduler - Outbox 메시지 릴레이 스케줄링 레이어
 *
 */
@Component
@ConditionalOnProperty(
    name = ["scheduler.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class OutboxRelayScheduler(
    private val outboxRelayService: OutboxRelayService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    fun relayNewMessages() {
        log.info("[OutboxRelayScheduler] relayNewMessages started")
        val result = outboxRelayService.relayNewMessages()
        log.info(
            "[OutboxRelayScheduler] relayNewMessages completed: success={}, failed={}, lastOffset={}",
            result.successCount,
            result.failedCount,
            result.lastProcessedId,
        )
    }

    @Scheduled(fixedDelay = 5000)
    fun retryFailedMessages() {
        log.info("[OutboxRelayScheduler] retryFailedMessages started")
        val result = outboxRelayService.retryFailedMessages()
        log.info(
            "[OutboxRelayScheduler] retryFailedMessages completed: success={}, failed={}",
            result.successCount,
            result.failedCount,
        )
    }
}
