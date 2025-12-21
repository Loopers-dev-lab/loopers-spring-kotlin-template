package com.loopers.domain.payment.event

import com.loopers.domain.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 결제 완료 이벤트
 * - order-events 토픽으로 발행 (key=orderId)
 */
data class PaymentCompletedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "PAYMENT_COMPLETED",
    override val aggregateId: Long, // orderId (partitionKey)
    override val occurredAt: Instant = Instant.now(),
    val paymentId: Long,
    val orderId: Long,
    val memberId: String,
    val amount: Long,
    val completedAt: Instant = Instant.now(),
) : DomainEvent
