package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZonedDateTime

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun findBy(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderId(orderId)
    }

    override fun findBy(transactionKey: String): Payment? {
        return paymentJpaRepository.findByTransactionKey(transactionKey)
    }

    override fun findPending(status: PaymentStatus, createdAt: ZonedDateTime): List<Payment> {
        return paymentJpaRepository.findByStatusAndCreatedAtBefore(status, createdAt)
    }

    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }
}
