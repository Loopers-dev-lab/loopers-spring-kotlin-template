package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(private val paymentJpaRepository: PaymentJpaRepository) : PaymentRepository {
    override fun save(payment: PaymentModel): PaymentModel = paymentJpaRepository.saveAndFlush(payment)

    override fun findByTransactionKey(transactionKey: String): PaymentModel? =
        paymentJpaRepository.findByTransactionKey(transactionKey)

    override fun findFirstByRefOrderKeyAndStatusOrderByCreatedAtDesc(
        refOrderKey: String,
        status: PaymentStatus,
    ): PaymentModel? = paymentJpaRepository.findFirstByRefOrderKeyAndStatusOrderByCreatedAtDesc(refOrderKey, status)
}
