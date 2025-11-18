package com.loopers.domain.order

interface PaymentRepository {
    fun findByOrderId(orderId: Long): Payment?
    fun save(payment: Payment): Payment
}
