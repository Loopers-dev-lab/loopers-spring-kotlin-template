package com.loopers.domain.product.vo

import com.loopers.domain.product.policy.ProductOptionValidator

@JvmInline
value class ProductOptionColor(
    val value: String,
) {
    init {
        ProductOptionValidator.validateColor(value)
    }
}
