package com.loopers.domain.payment.event

data class PaymentCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val memberId: String,
    val amount: Long,
    val completedAt: String
)
