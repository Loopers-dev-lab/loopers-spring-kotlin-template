package com.loopers.domain.payment

import com.loopers.domain.payment.entity.Payment

interface PaymentRepository {
    fun find(id: Long): Payment?

    fun save(payment: Payment): Payment
}
