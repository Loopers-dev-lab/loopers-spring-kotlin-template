package com.loopers.domain.order.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.vo.OrderFinalPrice
import com.loopers.domain.order.vo.OrderOriginalPrice
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

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

    enum class Status {
        ORDER_REQUEST,
        PAYMENT_REQUEST,
        ORDER_SUCCESS,
        ORDER_FAIL,
    }

    fun paymentRequest() {
        status = Status.PAYMENT_REQUEST
    }

    fun success() {
        status = Status.ORDER_SUCCESS
    }

    fun failed() {
        status = Status.ORDER_FAIL
    }

    companion object {
        fun create(userId: Long, originalPrice: BigDecimal, finalPrice: BigDecimal, status: Status): Order {
            return Order(userId, OrderOriginalPrice(originalPrice), OrderFinalPrice(finalPrice), status)
        }
    }
}
