package com.loopers.domain.order.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.vo.OrderFinalPrice
import com.loopers.domain.order.vo.OrderOriginalPrice
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
@Table(name = "orders")
class Order protected constructor(
    userId: Long,
    originalPrice: OrderOriginalPrice,
    finalPrice: OrderFinalPrice,
    status: Status,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "original_price", nullable = false)
    var originalPrice: OrderOriginalPrice = originalPrice
        protected set

    @Column(name = "final_price", nullable = false)
    var finalPrice: OrderFinalPrice = finalPrice
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

    enum class Status {
        ORDER_REQUEST,
        PAYMENT_REQUEST,
        ORDER_SUCCESS,
        ORDER_FAIL,
    }

    fun paymentRequest() {
        if (status != Status.ORDER_REQUEST) {
            throw CoreException(ErrorType.CONFLICT, "ORDER_REQUEST 상태에서만 결제 요청이 가능합니다.")
        }
        status = Status.PAYMENT_REQUEST
    }

    fun success() {
        if (status != Status.PAYMENT_REQUEST) {
            throw CoreException(ErrorType.CONFLICT, "결제 요청 상태에서만 주문 성공이 가능합니다.")
        }
        status = Status.ORDER_SUCCESS
    }

    fun failure(reason: String?) {
        if (status != Status.PAYMENT_REQUEST) {
            throw CoreException(ErrorType.CONFLICT, "결제 요청 상태에서만 주문 실패가 가능합니다.")
        }
        status = Status.ORDER_FAIL
        this.reason = reason
        failedAt = ZonedDateTime.now()
    }

    companion object {
        fun create(userId: Long, originalPrice: BigDecimal, finalPrice: BigDecimal, status: Status): Order {
            return Order(userId, OrderOriginalPrice(originalPrice), OrderFinalPrice(finalPrice), status)
        }
    }
}
