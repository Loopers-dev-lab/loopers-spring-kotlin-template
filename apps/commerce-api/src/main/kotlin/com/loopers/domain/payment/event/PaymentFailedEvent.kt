package com.loopers.domain.payment.event

import java.time.Instant

data class PaymentFailedEvent(
    val paymentId: Long,
    val orderId: Long,
    val reason: String,
    val failedAt: Instant
)
