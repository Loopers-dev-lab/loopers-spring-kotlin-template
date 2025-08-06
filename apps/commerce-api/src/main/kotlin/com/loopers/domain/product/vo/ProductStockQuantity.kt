package com.loopers.domain.product.vo

import com.loopers.domain.product.policy.ProductStockValidator
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class ProductStockQuantity(
    @Column(name = "quantity", nullable = false)
    val value: Int,
) {
    init {
        ProductStockValidator.validateQuantity(value)
    }

    fun decrease(amount: Int): ProductStockQuantity {
        return ProductStockQuantity(value - amount)
    }
}
