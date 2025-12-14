package com.loopers.interfaces.scheduler

import com.loopers.application.outbox.OutboxFacade
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Outbox 테이블을 주기적으로 폴링하여 Kafka로 이벤트를 발행하는 스케줄러
 *
 * - PENDING, FAILED 상태의 이벤트를 조회하여 발행
 */
@Component
class OutboxPollScheduler(
    private val outboxFacade: OutboxFacade,
) {
    /**
     * 1초마다 Outbox 테이블을 폴링하여 이벤트 발행
     */
    @Scheduled(fixedDelay = 1000)
    fun poll() {
        outboxFacade.publishPendingEvents()
    }
}
