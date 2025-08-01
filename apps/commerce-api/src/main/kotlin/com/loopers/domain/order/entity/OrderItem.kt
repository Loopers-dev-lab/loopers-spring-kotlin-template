package com.loopers.domain.order.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.vo.OrderItemQuantity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "order_item")
class OrderItem protected constructor(
    orderId: Long,
    productOptionId: Long,
    quantity: OrderItemQuantity,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Column(name = "product_option_id", nullable = false)
    var productOptionId: Long = productOptionId
        protected set

    @Column(name = "quantity", nullable = false)
    var quantity: OrderItemQuantity = quantity
        protected set

    companion object {
        fun create(orderId: Long, productOptionId: Long, quantity: Int): OrderItem {
            return OrderItem(orderId, productOptionId, OrderItemQuantity(quantity))
        }
    }
}
