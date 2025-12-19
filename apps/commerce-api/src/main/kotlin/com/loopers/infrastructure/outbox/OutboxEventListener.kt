package com.loopers.infrastructure.outbox

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.order.OrderCanceledEventV1
import com.loopers.domain.order.OrderCreatedEventV1
import com.loopers.domain.payment.PaymentCreatedEventV1
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.payment.PaymentPaidEventV1
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
 * - aggregateType, aggregateId는 이벤트 타입별로 추출
 * - 트랜잭션이 롤백되면 Outbox INSERT도 함께 롤백됨
 */
@Component
class OutboxEventListener(
    private val cloudEventEnvelopeFactory: CloudEventEnvelopeFactory,
    private val outboxRepository: OutboxRepository,
) {

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun onDomainEvent(event: DomainEvent) {
        val (aggregateType, aggregateId) = extractAggregate(event)
        val envelope = cloudEventEnvelopeFactory.create(event, aggregateType, aggregateId)
        outboxRepository.save(Outbox.from(envelope))
    }

    private fun extractAggregate(event: DomainEvent): Pair<String, String> = when (event) {
        is OrderCreatedEventV1 -> "Order" to event.orderId.toString()
        is OrderCanceledEventV1 -> "Order" to event.orderId.toString()
        is PaymentCreatedEventV1 -> "Payment" to event.paymentId.toString()
        is PaymentPaidEventV1 -> "Payment" to event.paymentId.toString()
        is PaymentFailedEventV1 -> "Payment" to event.paymentId.toString()
        is LikeCreatedEventV1 -> "Like" to event.productId.toString()
        is LikeCanceledEventV1 -> "Like" to event.productId.toString()
        else -> throw IllegalArgumentException("Unknown event type: ${event::class.simpleName}")
    }
}
