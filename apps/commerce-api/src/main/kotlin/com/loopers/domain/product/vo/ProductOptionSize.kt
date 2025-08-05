package com.loopers.domain.product.vo

import com.loopers.domain.product.policy.ProductOptionValidator

@JvmInline
value class ProductOptionSize(
    val value: String,
) {
    init {
        ProductOptionValidator.validateSize(value)
    }
}
