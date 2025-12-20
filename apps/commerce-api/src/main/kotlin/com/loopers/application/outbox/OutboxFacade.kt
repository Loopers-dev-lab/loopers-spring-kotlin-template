package com.loopers.application.outbox

import com.loopers.domain.outbox.OutboxEventPublisher
import com.loopers.domain.outbox.OutboxService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Outbox 이벤트 발행 파사드
 *
 * Outbox 테이블의 이벤트를 조회하여 Kafka로 발행하는 비즈니스 로직 담당
 */
@Component
class OutboxFacade(
    private val outboxService: OutboxService,
    private val outboxEventPublisher: OutboxEventPublisher,
) {
    private val log = LoggerFactory.getLogger(OutboxFacade::class.java)

    companion object {
        private const val DEFAULT_BATCH_SIZE = 100
    }

    /**
     * 대기 중인 이벤트를 조회하여 Kafka로 발행
     *
     * @param batchSize 한 번에 처리할 이벤트 수
     * @return 처리 결과 (성공 수, 실패 수)
     */
    fun publishPendingEvents(batchSize: Int = DEFAULT_BATCH_SIZE): PublishResult {
        val events = outboxService.findPendingEvents(batchSize)

        if (events.isEmpty()) {
            return PublishResult(0, 0)
        }

        log.info("Outbox 이벤트 발행 시작: {}개", events.size)

        var successCount = 0
        var failCount = 0

        for (event in events) {
            try {
                // 상태를 PROCESSING으로 변경
                outboxService.markProcessing(event)

                // Kafka로 발행
                val success = outboxEventPublisher.publish(event)

                if (success) {
                    outboxService.markCompleted(event.id)
                    successCount++
                } else {
                    outboxService.markFailed(event.id, "Kafka 발행 실패")
                    failCount++
                }
            } catch (e: Exception) {
                log.error("Outbox 이벤트 처리 실패: id={}", event.id, e)
                outboxService.markFailed(event.id, e.message ?: "Unknown error")
                failCount++
            }
        }

        log.info("Outbox 이벤트 발행 완료: 성공={}, 실패={}", successCount, failCount)

        return PublishResult(successCount, failCount)
    }

    data class PublishResult(
        val successCount: Int,
        val failCount: Int,
    )
}
