package com.loopers.infrastructure.outbox

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * OutboxRelayScheduler - Outbox 메시지 릴레이 스케줄링 레이어
 *
 * 순서 보장 전략:
 * - 단일 relay() 스케줄러로 통합
 * - 실패한 메시지는 Outbox에서 재시도 (HOL blocking)
 * - 만료된 메시지만 OutboxFailed로 이동
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
    fun relay() {
        val result = outboxRelayService.relay()
        if (result.hasActivity()) {
            log.info(
                "[OutboxRelayScheduler] relay completed: success={}, failed={}, lastOffset={}",
                result.successCount,
                result.failedCount,
                result.lastProcessedId,
            )
        }
    }
}
