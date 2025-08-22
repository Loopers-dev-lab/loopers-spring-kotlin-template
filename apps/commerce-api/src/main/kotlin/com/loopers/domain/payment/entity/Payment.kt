package com.loopers.domain.payment.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.vo.PaymentPrice
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.ZonedDateTime

@Entity
@Table(name = "payment")
class Payment protected constructor(
    orderId: Long,
    paymentMethod: Method,
    paymentPrice: PaymentPrice,
    status: Status,
    cardType: String?,
    cardNumber: String?,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: Method = paymentMethod
        protected set

    @Column(name = "payment_price", nullable = false)
    var paymentPrice: PaymentPrice = paymentPrice
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: Status = status
        protected set

    @Column(name = "card_type")
    var cardType: String? = cardType
        protected set

    @Column(name = "card_number")
    var cardNumber: String? = cardNumber
        protected set

    @Column(name = "transaction_key")
    var transactionKey: String? = null
        protected set

    @Column(name = "reason")
    var reason: String? = null
        protected set

    @Column(name = "failed_at")
    var failedAt: ZonedDateTime? = null
        protected set

    fun processing() {
        if (status != Status.REQUESTED) {
            throw CoreException(ErrorType.CONFLICT, "결제 요청 상태일 때만 결제를 진행할 수 있습니다.")
        }
        status = Status.PROCESSING
    }

    fun success() {
        if (status != Status.PROCESSING) {
            throw CoreException(ErrorType.CONFLICT, "결제 진행 상태일 때만 결제를 성공 처리할 수 있습니다.")
        }
        status = Status.SUCCESS
    }

    fun failure(reason: String?) {
        if (status != Status.PROCESSING) {
            throw CoreException(ErrorType.CONFLICT, "결제 진행 상태일 때만 결제를 실패 처리할 수 있습니다.")
        }
        status = Status.FAILED
        this.reason = reason
        failedAt = ZonedDateTime.now()
    }

    fun updateTransactionKey(transactionKey: String) {
        this.transactionKey = transactionKey
    }

    enum class Method {
        POINT,
        CREDIT_CARD,
    }
    enum class Status {
        REQUESTED,
        PROCESSING,
        SUCCESS,
        FAILED,
    }

    companion object {
        fun create(orderId: Long, paymentMethod: Method, paymentPrice: BigDecimal, status: Status): Payment {
            return Payment(
                orderId,
                paymentMethod,
                PaymentPrice(paymentPrice),
                status,
                cardType = null,
                cardNumber = null,
            )
        }

        fun create(
            orderId: Long,
            paymentMethod: Method,
            paymentPrice: BigDecimal,
            status: Status,
            cardType: String,
            cardNumber: String,
        ): Payment {
            return Payment(orderId, paymentMethod, PaymentPrice(paymentPrice), status, cardType, cardNumber)
        }
    }
}
