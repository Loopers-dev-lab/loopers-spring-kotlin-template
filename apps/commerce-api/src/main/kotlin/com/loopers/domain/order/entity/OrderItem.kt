package com.loopers.domain.order.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.vo.OrderItemQuantity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal

@Entity
@Table(
    name = "order_item",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_order_item_order_product_option",
            columnNames = ["order_id", "product_option_id"],
        ),
    ],
)
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

    fun calculatePrice(
        basePrice: BigDecimal,
        additionalPrice: BigDecimal,
    ): BigDecimal {
        return (basePrice + additionalPrice) * quantity.value.toBigDecimal()
    }

    companion object {
        fun create(orderId: Long, productOptionId: Long, quantity: Int): OrderItem {
            return OrderItem(orderId, productOptionId, OrderItemQuantity(quantity))
        }
    }
}
