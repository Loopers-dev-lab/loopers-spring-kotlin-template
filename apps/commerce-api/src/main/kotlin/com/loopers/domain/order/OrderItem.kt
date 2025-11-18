package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Product
import com.loopers.domain.product.Quantity
import com.loopers.domain.shared.Money
import jakarta.persistence.*

@Entity
@Table(name = "order_items")
class OrderItem(
    product: Product,
    quantity: Quantity,
) : BaseEntity() {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product = product
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "quantity", nullable = false))
    var quantity: Quantity = quantity
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "price", nullable = false))
    var price: Money = product.price
        protected set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "subtotal", nullable = false))
    var subtotal: Money = calculateSubtotal()
        protected set

    internal fun assignOrder(order: Order) {
        this.order = order
    }

    fun calculateSubtotal(): Money {
        return price.multiply(quantity.value)
    }

    companion object {
        fun of(product: Product, quantity: Quantity): OrderItem {
            return OrderItem(product, quantity)
        }
    }
}
