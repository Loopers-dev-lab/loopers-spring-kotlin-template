package com.loopers.infrastructure.payment

import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.ZonedDateTime

interface PaymentJpaRepository : JpaRepository<Payment, Long> {

    fun findByTransactionKey(transactionKey: String): Payment?

    fun findByOrderId(orderId: Long): Payment?

    fun findByStatusAndCreatedAtBefore(status: PaymentStatus, createdAt: ZonedDateTime): List<Payment>

    @Modifying
    @Query("UPDATE Payment SET transactionKey = :transactionKey where id = :id")
    fun update(id: Long, transactionKey: String)

    @Modifying
    @Query("UPDATE Payment SET status = :status, reason = :reason where id = :id")
    fun update(id: Long, status: PaymentStatus, reason: String)
}
