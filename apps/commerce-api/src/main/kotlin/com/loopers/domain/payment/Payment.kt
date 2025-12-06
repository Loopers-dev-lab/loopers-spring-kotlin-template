package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.dto.PaymentCommand
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
        Index(name = "idx_payment_user_id", columnList = "ref_user_id"),
        Index(name = "idx_payment_order_id", columnList = "ref_order_id"),
    ],
)
class Payment(

    @Column(name = "transaction_key")
    var transactionKey: String? = null,

    @Enumerated(value = STRING)
    @Column(name = "card_type", nullable = false)
    val cardType: CardType,

    @Column(name = "card_no", nullable = false)
    val cardNo: String,

    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Enumerated(value = STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING,

    @Column(name = "reason")
    var reason: String? = null,

    @Column(name = "ref_order_id", nullable = false)
    val orderId: Long,

    @Column(name = "ref_user_id", nullable = false)
    val userId: Long,

    ) : BaseEntity() {

    companion object {
        fun create(command: PaymentCommand.Create): Payment {
            return Payment(
                cardType = command.cardType,
                cardNo = command.cardNo,
                amount = command.amount,
                orderId = command.orderId,
                userId = command.userId,
            )
        }
    }

    fun updateTransactionKey(transactionKey: String) {
        this.transactionKey = transactionKey
    }

    fun success() {
        this.status = PaymentStatus.SUCCESS
    }

    fun fail(reason: String?) {
        this.status = PaymentStatus.FAILED
        this.reason = reason
    }
}
