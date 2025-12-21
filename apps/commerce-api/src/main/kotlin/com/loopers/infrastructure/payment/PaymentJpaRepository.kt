package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<PaymentModel, Long> {
    fun findByTransactionKey(transactionKey: String): PaymentModel?

    fun findFirstByRefOrderKeyAndStatusOrderByCreatedAtDesc(
        refOrderKey: String,
        status: PaymentStatus,
    ): PaymentModel?
}
