package com.loopers.domain.payment

interface PaymentRepository {
    fun findBy(orderId: Long): Payment?
    fun findPending(status: PaymentStatus, createdAt: java.time.LocalDateTime): List<Payment>
    fun save(payment: Payment): Payment
}
