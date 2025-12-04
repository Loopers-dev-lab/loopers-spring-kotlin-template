package com.loopers.domain.payment

import java.time.ZonedDateTime

interface PaymentRepository {
    fun findBy(orderId: Long): Payment?
    fun findBy(transactionKey: String): Payment?
    fun findPending(status: PaymentStatus, createdAt: ZonedDateTime): List<Payment>
    fun save(payment: Payment): Payment
}
