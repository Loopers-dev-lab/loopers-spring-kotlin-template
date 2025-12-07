package com.loopers.domain.payment

import org.springframework.data.domain.Slice

interface PaymentRepository {
    fun findById(id: Long): Payment?
    fun findByOrderId(orderId: Long): Payment?
    fun findByExternalPaymentKey(key: String): Payment?
    fun findAllBy(query: PaymentPageQuery): Slice<Payment>

    fun save(payment: Payment): Payment
}
