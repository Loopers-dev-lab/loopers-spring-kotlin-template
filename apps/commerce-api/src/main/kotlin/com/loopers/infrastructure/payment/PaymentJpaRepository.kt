package com.loopers.infrastructure.payment

import com.loopers.domain.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByTransactionKey(transactionKey: String): Payment?
}
