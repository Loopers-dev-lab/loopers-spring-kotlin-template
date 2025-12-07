package com.loopers.infrastructure.order

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
@Repository
interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: Long): Payment?
    fun findByExternalPaymentKey(key: String): Payment?
    fun findByStatusInOrderByCreatedAtAsc(statuses: List<PaymentStatus>): List<Payment>
}
