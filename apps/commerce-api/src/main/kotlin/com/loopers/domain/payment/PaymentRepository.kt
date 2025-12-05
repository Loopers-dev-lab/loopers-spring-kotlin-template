package com.loopers.domain.payment

import java.time.ZonedDateTime

interface PaymentRepository {
    fun findById(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?
    fun findByExternalPaymentKey(key: String): Payment?
    fun findByStatusInAndUpdatedAtBefore(
        statuses: List<PaymentStatus>,
        before: ZonedDateTime,
    ): List<Payment>

    fun save(payment: Payment): Payment
}
