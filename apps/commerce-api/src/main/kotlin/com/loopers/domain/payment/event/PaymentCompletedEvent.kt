package com.loopers.domain.payment.event

import java.time.Instant

data class PaymentCompletedEvent(
    val paymentId: Long,
    val orderId: Long,
    val memberId: String,
    val amount: Long,
    val completedAt: Instant
)
