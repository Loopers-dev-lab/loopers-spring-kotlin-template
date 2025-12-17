package com.loopers.domain.payment

import com.loopers.support.event.DomainEvent
import java.time.Instant

data class PaymentPaidEventV1(
    val paymentId: Long,
    val orderId: Long,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent {
    companion object {
        fun from(payment: Payment): PaymentPaidEventV1 {
            return PaymentPaidEventV1(
                paymentId = payment.id,
                orderId = payment.orderId,
            )
        }
    }
}
