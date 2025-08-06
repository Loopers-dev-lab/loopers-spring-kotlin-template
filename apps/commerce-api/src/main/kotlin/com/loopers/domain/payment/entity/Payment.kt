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

    @Column(name = "reason")
    var reason: String? = null

    @Column(name = "failed_at")
    var failedAt: ZonedDateTime? = null
        protected set

    fun success() {
        if (status != Status.REQUESTED) {
            throw CoreException(ErrorType.CONFLICT, "결제 요청 상태일 때만 주문을 성공 처리할 수 있습니다.")
        }
        status = Status.SUCCESS
    }

    fun failure(reason: String) {
        if (status != Status.REQUESTED) {
            throw CoreException(ErrorType.CONFLICT, "결제 요청 상태일 때만 주문을 실패 처리할 수 있습니다.")
        }
        status = Status.FAILED
        this.reason = reason
        failedAt = ZonedDateTime.now()
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
