package com.loopers.domain.payment

interface PaymentRepository {
    fun findById(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?
    fun findByExternalPaymentKey(key: String): Payment?
    fun findByStatusIn(statuses: List<PaymentStatus>): List<Payment>

    fun save(payment: Payment): Payment
}
