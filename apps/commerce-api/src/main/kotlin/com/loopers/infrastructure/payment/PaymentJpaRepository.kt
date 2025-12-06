package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByTransactionKey(transactionKey: String): Payment?

    fun findByOrderIdAndDeletedAtIsNull(orderId: Long): List<Payment>

    @Query(
        """
        SELECT p FROM Payment p
        WHERE p.status = :status
        AND p.createdAt < :cutoffTime
        AND p.deletedAt IS NULL
        """,
    )
    fun findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
        @Param("status") status: PaymentStatus,
        @Param("cutoffTime") cutoffTime: LocalDateTime,
    ): List<Payment>
}
