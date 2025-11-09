package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Price
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "order_items")
class OrderItem(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "product_name", nullable = false, length = 200)
    val productName: String,

    @Column(name = "brand_id", nullable = false)
    val brandId: Long,

    @Column(name = "brand_name", nullable = false, length = 100)
    val brandName: String,

    @Column(name = "brand_description", columnDefinition = "TEXT")
    val brandDescription: String?,

    quantity: Int,

    priceAtOrder: Price,
) : BaseEntity() {
    @Column(nullable = false, updatable = false)
    val quantity: Int = quantity

    @Embedded
    @AttributeOverrides(
        AttributeOverride(
            name = "amount",
            column = Column(name = "price_at_order", nullable = false, precision = 15, scale = 2, updatable = false),
        ),
        AttributeOverride(name = "currency", column = Column(name = "currency", nullable = false, length = 3, updatable = false)),
    )
    val priceAtOrder: Price = priceAtOrder

    init {
        if (quantity <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 1개 이상이어야 합니다.")
        }
    }

    fun calculateItemAmount(): Money {
        val totalAmount = priceAtOrder.amount * quantity.toBigDecimal()
        return Money(amount = totalAmount, currency = priceAtOrder.currency)
    }
}
