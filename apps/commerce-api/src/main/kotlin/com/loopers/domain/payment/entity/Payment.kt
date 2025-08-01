package com.loopers.domain.payment.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.vo.PaymentPrice
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "payment")
class Payment protected constructor(
    orderId: Long,
    paymentMethod: Method,
    paymentPrice: PaymentPrice,
    status: Status,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

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

    fun success() {
        status = Status.SUCCESS
    }

    fun failure() {
        status = Status.FAILED
    }

    enum class Method {
        POINT,
    }
    enum class Status {
        REQUESTED,
        SUCCESS,
        FAILED,
    }

    companion object {
        fun create(orderId: Long, paymentMethod: Method, paymentPrice: BigDecimal, status: Status): Payment {
            return Payment(orderId, paymentMethod, PaymentPrice(paymentPrice), status)
        }
    }
}
