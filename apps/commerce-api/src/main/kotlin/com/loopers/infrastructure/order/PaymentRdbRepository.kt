package com.loopers.infrastructure.order

import com.loopers.domain.order.Payment
import com.loopers.domain.order.PaymentRepository
import com.loopers.domain.order.PaymentStatus
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

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

    override fun findByStatusInAndUpdatedAtBefore(
        statuses: List<PaymentStatus>,
        before: ZonedDateTime,
    ): List<Payment> {
        return paymentJpaRepository.findByStatusInAndUpdatedAtBefore(statuses, before)
    }

    override fun save(payment: Payment): Payment {
        return paymentJpaRepository.save(payment)
    }
}
