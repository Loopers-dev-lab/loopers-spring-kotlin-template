package com.loopers.infrastructure.order

import com.loopers.domain.order.Payment
import com.loopers.domain.order.PaymentRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentRdbRepository(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {
    override fun findByOrderId(orderId: Long): Payment? {
        return paymentJpaRepository.findById(orderId).orElse(null)
    }

    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }
}
