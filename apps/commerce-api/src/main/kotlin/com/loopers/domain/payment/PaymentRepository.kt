package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?
}
