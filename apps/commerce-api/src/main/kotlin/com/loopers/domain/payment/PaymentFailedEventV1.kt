package com.loopers.domain.payment

import com.loopers.support.event.DomainEvent
import com.loopers.support.values.Money
import java.time.Instant
import java.util.UUID

data class PaymentFailedEventV1(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val usedPoint: Money,
    val issuedCouponId: Long?,
    override val eventId: String = UUID.randomUUID().toString(),
    override val eventType: String = "PaymentFailedEvent",
    override val aggregateId: String = paymentId.toString(),
    override val aggregateType: String = "Payment",
    override val occurredAt: Instant = Instant.now(),
    override val version: Int = 1,
) : DomainEvent {
    companion object {
        fun from(payment: Payment): PaymentFailedEventV1 {
            return PaymentFailedEventV1(
                paymentId = payment.id,
                orderId = payment.orderId,
                userId = payment.userId,
                usedPoint = payment.usedPoint,
                issuedCouponId = payment.issuedCouponId,
            )
        }
    }
}
