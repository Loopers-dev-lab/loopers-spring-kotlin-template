package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "payment",
    indexes = [
        Index(name = "uk_payment_transaction_key", columnList = "transaction_key", unique = true),
        Index(name = "idx_payment_status_created_at", columnList = "status, created_at"),
        Index(name = "idx_orders_user_id", columnList = "ref_user_id"),
        Index(name = "idx_orders_order_id", columnList = "ref_order_id"),
    ],
)
class Payment(

    @Column(name = "transaction_key", nullable = false)
    val transactionKey: String,

    @Enumerated(value = STRING)
    @Column(name = "card_type", nullable = false)
    val cardType: CardType,

    @Column(name = "card_no", nullable = false)
    val cardNo: String,

    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Enumerated(value = STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus,

    @Column(name = "reason")
    val reason: String,

    @Column(name = "ref_order_id", nullable = false)
    val orderId: Long,

    @Column(name = "ref_user_id", nullable = false)
    val userId: Long,

    ) : BaseEntity()
