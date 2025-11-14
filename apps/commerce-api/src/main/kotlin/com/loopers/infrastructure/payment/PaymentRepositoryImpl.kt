package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }

    override fun findById(id: Long): Payment? {
        return paymentJpaRepository.findByIdOrNull(id)
    }

    override fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findByOrderId(orderId)
    }
}
