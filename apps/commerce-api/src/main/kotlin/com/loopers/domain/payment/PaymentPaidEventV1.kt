package com.loopers.domain.payment

import com.loopers.support.event.DomainEvent
import java.time.Instant
import java.util.UUID

data class PaymentPaidEventV1(
    val paymentId: Long,
    val orderId: Long,
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "PaymentPaidEvent",
    override val aggregateId: String = paymentId.toString(),
    override val aggregateType: String = "Payment",
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1,
) : DomainEvent
