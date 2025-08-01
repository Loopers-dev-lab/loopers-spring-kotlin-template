package com.loopers.domain.product.vo

import com.loopers.domain.product.policy.ProductOptionValidator

@JvmInline
value class ProductOptionDisplayName(
    val value: String,
) {
    init {
        ProductOptionValidator.validateDisplayName(value)
    }
}
