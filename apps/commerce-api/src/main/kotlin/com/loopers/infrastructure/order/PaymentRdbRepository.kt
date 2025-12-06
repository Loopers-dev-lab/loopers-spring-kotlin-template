package com.loopers.infrastructure.order

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
@Repository
class PaymentRdbRepository(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun findById(id: Long): Payment? {
        return paymentJpaRepository.findByIdOrNull(id)
    }

    override fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderId(orderId)
    }

    override fun findByExternalPaymentKey(key: String): Payment? {
        return paymentJpaRepository.findByExternalPaymentKey(key)
    }

    override fun findByStatusIn(statuses: List<PaymentStatus>): List<Payment> {
        return paymentJpaRepository.findByStatusIn(statuses)
    }

    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }
}
