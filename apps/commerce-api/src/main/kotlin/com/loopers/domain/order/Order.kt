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
    var status: OrderStatus,

    @Column(nullable = false)
    val totalAmount: Long,

    @Column(name = "ref_user_id", nullable = false)
    val userId: Long,
) : BaseEntity() {

    companion object {
        fun create(totalAmount: Long, userId: Long): Order {
            require(totalAmount > 0) { "주문 총액은 0보다 커야 합니다." }

            return Order(
                status = OrderStatus.PENDING,
                totalAmount = totalAmount,
                userId = userId,
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

    fun cancel() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(ErrorType.ORDER_NOT_CANCELLABLE)
        }
        this.status = OrderStatus.CANCELLED
    }
}
