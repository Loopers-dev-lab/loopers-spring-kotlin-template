package com.loopers.domain.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "commerce_payments")
class Payment(
    orderId: Long,
    amount: Money,
    paymentMethod: PaymentMethod = PaymentMethod.POINT,
    transactionKey: String? = null,
    cardType: String? = null,
    cardNumber: CardNumber? = null,
) : BaseEntity() {

    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "amount", nullable = false))
    var amount: Money = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: PaymentMethod = paymentMethod
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING
        protected set

    @Column(name = "transaction_key", length = 100)
    var transactionKey: String? = transactionKey
        protected set

    @Column(name = "card_type", length = 20)
    var cardType: String? = cardType
        protected set

    @Embedded
    var cardNumber: CardNumber? = cardNumber
        protected set

    @Column(name = "failure_reason", length = 500)
    var failureReason: String? = null
        protected set

    fun markAsSuccess() {
        if (status != PaymentStatus.PENDING) {
            throw CoreException(ErrorType.INVALID_PAYMENT_STATUS)
        }
        status = PaymentStatus.SUCCESS
    }

    fun markAsFailed(reason: String) {
        if (status != PaymentStatus.PENDING) {
            throw CoreException(ErrorType.INVALID_PAYMENT_STATUS)
        }
        status = PaymentStatus.FAILED
        failureReason = reason
    }


    companion object {
        fun createCardPayment(
            orderId: Long,
            amount: Money,
            transactionKey: String,
            cardType: String,
            cardNo: String,
        ): Payment {
            return Payment(
                orderId = orderId,
                amount = amount,
                paymentMethod = PaymentMethod.CARD,
                transactionKey = transactionKey,
                cardType = cardType,
                cardNumber = CardNumber.from(cardNo),
            )
        }

        fun createFailedPayment(
            orderId: Long,
            amount: Money,
            reason: String,
        ): Payment {
            return Payment(
                orderId = orderId,
                amount = amount,
            ).apply {
                status = PaymentStatus.FAILED
                failureReason = reason
            }
        }
    }

}
