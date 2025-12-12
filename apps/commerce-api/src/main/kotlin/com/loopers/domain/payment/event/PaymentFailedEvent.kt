package com.loopers.domain.payment.event

data class PaymentFailedEvent(
    val paymentId: Long,
    val orderId: Long,
    val reason: String,
    val failedAt: String
)
