package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_orders_user_id", columnList = "ref_user_id"),
    ],
)
class Order(

    @Column(name = "status", nullable = false)
    @Enumerated(value = STRING)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(nullable = false)
    val totalAmount: Long,

    @Column(name = "ref_user_id", nullable = false)
    val userId: Long,

    @Column(name = "ref_coupon_id", nullable = true)
    val couponId: Long? = null,
) : BaseEntity() {

    companion object {
        fun create(totalAmount: Long, userId: Long, couponId: Long? = null, status: OrderStatus = OrderStatus.PENDING): Order {
            require(totalAmount > 0) { "주문 총액은 0보다 커야 합니다." }

            return Order(
                status = status,
                totalAmount = totalAmount,
                userId = userId,
                couponId = couponId,
            )
        }
    }

    fun validateOwner(userId: Long) {
        if (this.userId != userId) {
            throw CoreException(ErrorType.FORBIDDEN)
        }
    }

    fun complete() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(ErrorType.ORDER_NOT_COMPLETABLE)
        }
        this.status = OrderStatus.COMPLETED
    }

    fun paymentFailed() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(ErrorType.ORDER_NOT_PAYMENT_FAILED)
        }
        this.status = OrderStatus.PAYMENT_FAILED
    }

    fun cancel() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(ErrorType.ORDER_NOT_CANCELLABLE)
        }
        this.status = OrderStatus.CANCELLED
    }
}
