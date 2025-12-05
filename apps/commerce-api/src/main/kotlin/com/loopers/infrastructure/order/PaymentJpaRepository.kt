package com.loopers.infrastructure.order

import com.loopers.domain.order.Payment
import com.loopers.domain.order.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByOrderId(orderId: Long): Payment?
    fun findByExternalPaymentKey(key: String): Payment?
    fun findByStatusInAndUpdatedAtBefore(
        statuses: List<PaymentStatus>,
        before: ZonedDateTime,
    ): List<Payment>
}
