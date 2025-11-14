package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: Long): Payment?
}
