package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "orders")
class Order(
    userId: Long,
    totalAmount: Money,
    status: OrderStatus,
    orderItems: MutableList<OrderItem> = mutableListOf(),
) : BaseEntity() {
    var userId: Long = userId
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    var totalAmount: Money = totalAmount
        private set

    @Enumerated(EnumType.STRING)
    var status: OrderStatus = status
        private set

    @OneToMany(
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY,
    )
    @JoinColumn(
        name = "order_id",
        nullable = false,
        foreignKey = ForeignKey(name = "fk_order_items_order_id"),
    )
    var orderItems: MutableList<OrderItem> = orderItems
        private set

    init {
        if (totalAmount < Money.ZERO_KRW) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.")
        }

        if (orderItems.count() == 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 상품이 없을 수 없습니다.")
        }
    }

    companion object {
        fun paid(userId: Long, orderItems: MutableList<OrderItem>): Order {
            val totalAmount = orderItems.fold(Money.ZERO_KRW) { acc, item ->
                acc + (item.unitPrice * item.quantity)
            }
            return Order(userId, totalAmount, OrderStatus.PAID, orderItems)
        }

        fun of(
            userId: Long,
            totalAmount: Money,
            status: OrderStatus,
            orderItems: MutableList<OrderItem> = mutableListOf(),
        ): Order {
            return Order(userId, totalAmount, status, orderItems)
        }
    }
}
