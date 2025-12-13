package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }

    override fun findById(paymentId: Long): Payment? {
        return paymentJpaRepository.findById(paymentId).orElse(null)
    }

    override fun findByTransactionKey(transactionKey: String): Payment? {
        return paymentJpaRepository.findByTransactionKey(transactionKey)
    }

    override fun findByOrderId(orderId: Long): List<Payment> {
        return paymentJpaRepository.findByOrderId(orderId)
    }

    override fun findByIdOrThrow(paymentId: Long): Payment {
        return findById(paymentId)
            ?: throw CoreException(ErrorType.PAYMENT_NOT_FOUND, "결제를 찾을 수 없습니다. id: $paymentId")
    }

}
