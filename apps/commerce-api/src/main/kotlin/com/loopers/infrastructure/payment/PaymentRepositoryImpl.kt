package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }

    override fun findByTransactionKey(transactionKey: String): Payment? {
        return paymentJpaRepository.findByTransactionKey(transactionKey)
    }

    override fun findByOrderId(orderId: Long): List<Payment> {
        return paymentJpaRepository.findByOrderId(orderId)
    }
}
