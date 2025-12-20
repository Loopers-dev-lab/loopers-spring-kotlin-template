package com.loopers.domain.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Outbox 이벤트 저장 및 관리 서비스
 *
 * 비즈니스 트랜잭션과 동일한 트랜잭션에서 이벤트를 저장하여
 * 메시지 발행의 원자성을 보장
 */
@Service
class OutboxService(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val DEFAULT_BATCH_SIZE = 100
    }

    /**
     * 이벤트를 Outbox에 저장
     * 호출하는 트랜잭션과 동일한 트랜잭션에서 실행됨
     */
    @Transactional
    fun save(
        aggregateType: AggregateType,
        aggregateId: String,
        eventType: String,
        payload: Any,
    ) {
        val payloadJson = objectMapper.writeValueAsString(payload)

        val outbox = Outbox.create(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payloadJson,
        )

        outboxRepository.save(outbox)
    }

    /**
     * 발행 대기 중인 이벤트 조회
     */
    @Transactional(readOnly = true)
    fun findPendingEvents(batchSize: Int = DEFAULT_BATCH_SIZE): List<Outbox> {
        return outboxRepository.findByStatusIn(
            statuses = listOf(OutboxStatus.PENDING, OutboxStatus.FAILED),
            pageable = PageRequest.of(0, batchSize),
        )
    }

    /**
     * 이벤트 상태를 PROCESSING으로 변경
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markProcessing(outbox: Outbox) {
        outbox.markProcessing()
        outboxRepository.save(outbox)
    }

    /**
     * 이벤트 발행 성공 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markCompleted(outboxId: Long) {
        val outbox =
            outboxRepository.findById(outboxId) ?: throw CoreException(ErrorType.NOT_FOUND, "outbox를 찾을 수 없습니다: $outboxId")
        outbox.markCompleted()
        outboxRepository.save(outbox)
    }

    /**
     * 이벤트 발행 실패 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(outboxId: Long, errorMessage: String) {
        val outbox =
            outboxRepository.findById(outboxId) ?: throw CoreException(ErrorType.NOT_FOUND, "outbox를 찾을 수 없습니다: $outboxId")
        outbox.markFailed(errorMessage)
        outboxRepository.save(outbox)
    }
}
