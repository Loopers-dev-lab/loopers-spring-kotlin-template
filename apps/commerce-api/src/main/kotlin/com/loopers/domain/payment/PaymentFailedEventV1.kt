package com.loopers.domain.payment

import com.loopers.support.event.DomainEvent
import com.loopers.support.values.Money
import java.time.Instant

data class PaymentFailedEventV1(
    val paymentId: Long,
    val orderId: Long,
    val userId: Long,
    val usedPoint: Money,
    val issuedCouponId: Long?,
    override val occurredAt: Instant = Instant.now(),
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
