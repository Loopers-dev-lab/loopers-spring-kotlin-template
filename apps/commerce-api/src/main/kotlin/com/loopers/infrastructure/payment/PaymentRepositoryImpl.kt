package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }

    override fun findById(id: Long): Payment? {
        return paymentJpaRepository.findById(id).orElse(null)
    }

    override fun findByTransactionKey(transactionKey: String): Payment? {
        return paymentJpaRepository.findByTransactionKey(transactionKey)
    }

    override fun findByOrderId(orderId: Long): List<Payment> {
        return paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId)
    }

    override fun findPendingPaymentsOlderThan(minutes: Long): List<Payment> {
        val cutoffTime = LocalDateTime.now().minusMinutes(minutes)
        return paymentJpaRepository.findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            PaymentStatus.PENDING,
            cutoffTime,
        )
    }
}
