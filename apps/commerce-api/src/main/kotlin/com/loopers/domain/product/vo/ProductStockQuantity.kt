package com.loopers.domain.product.vo

import com.loopers.domain.product.policy.ProductStockValidator

@JvmInline
value class ProductStockQuantity(
    val value: Int,
) {
    init {
        ProductStockValidator.validateQuantity(value)
    }

    fun decrease(amount: Int): ProductStockQuantity {
        return ProductStockQuantity(value - amount)
    }
}
