package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(private val paymentJpaRepository: PaymentJpaRepository) : PaymentRepository {
    override fun save(payment: PaymentModel): PaymentModel = paymentJpaRepository.saveAndFlush(payment)

    override fun findByTransactionKey(transactionKey: String): PaymentModel? =
        paymentJpaRepository.findByTransactionKey(transactionKey)
}
