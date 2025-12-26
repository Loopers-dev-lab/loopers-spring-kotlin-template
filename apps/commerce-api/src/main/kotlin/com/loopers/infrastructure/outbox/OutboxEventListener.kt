package com.loopers.infrastructure.outbox

import com.loopers.support.event.DomainEvent
import com.loopers.support.outbox.Outbox
import com.loopers.support.outbox.OutboxRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * OutboxEventListener - BEFORE_COMMIT 시점에 DomainEvent를 Outbox에 저장
 *
 * - 비즈니스 트랜잭션과 동일한 트랜잭션에서 Outbox INSERT 수행
 * - CloudEventEnvelopeFactory가 null을 반환하면 (알 수 없는 이벤트) 저장하지 않음
 * - 트랜잭션이 롤백되면 Outbox INSERT도 함께 롤백됨
 */
@Component
class OutboxEventListener(
    private val cloudEventEnvelopeFactory: CloudEventEnvelopeFactory,
    private val outboxRepository: OutboxRepository,
) {

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onDomainEvent(event: DomainEvent) {
        val envelope = cloudEventEnvelopeFactory.create(event) ?: return
        outboxRepository.save(Outbox.from(envelope))
    }
}
