package com.loopers.domain.order

interface PaymentRepository {
    fun save(payment: Payment): Payment
}
