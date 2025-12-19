package com.loopers.infrastructure.outbox

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * OutboxRelayScheduler - Outbox 메시지 릴레이 스케줄링 레이어
 *
 * - relayNewMessages: 500ms 간격으로 새 메시지 릴레이
 * - retryFailedMessages: 5000ms 간격으로 실패 메시지 재시도
 *
 * 비즈니스 로직은 OutboxRelayService에 위임하고, 스케줄링과 로깅만 담당합니다.
 */
@Component
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
