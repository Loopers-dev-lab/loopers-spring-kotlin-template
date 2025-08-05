package com.loopers.domain.product.vo

import com.loopers.domain.product.policy.ProductStockValidator
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class ProductStockQuantity(
    @Column(name = "quantity", nullable = false)
    val quantity: Int,
) {
    init {
        ProductStockValidator.validateQuantity(quantity)
    }

    fun decrease(amount: Int): ProductStockQuantity {
        return ProductStockQuantity(quantity - amount)
    }
}
