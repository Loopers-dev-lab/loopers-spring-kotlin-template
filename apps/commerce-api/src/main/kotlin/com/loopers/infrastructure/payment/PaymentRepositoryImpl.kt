package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.entity.Payment
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun find(id: Long): Payment? {
        return paymentJpaRepository.findByIdOrNull(id)
    }

    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }
}
