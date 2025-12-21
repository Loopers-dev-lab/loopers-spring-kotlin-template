package com.loopers.domain.event.payment

import com.loopers.domain.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 결제 실패 이벤트
 * - order-events 토픽으로 발행 (key=orderId)
 */
data class PaymentFailedEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "PAYMENT_FAILED",
    override val aggregateId: Long, // orderId (partitionKey)
    override val occurredAt: Instant = Instant.now(),

    val paymentId: Long,
    val orderId: Long,
    val reason: String,
    val failedAt: Instant = Instant.now(),
) : DomainEvent
