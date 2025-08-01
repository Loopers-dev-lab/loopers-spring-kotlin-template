package com.loopers.domain.product.vo

import com.loopers.domain.brand.policy.BrandValidator

@JvmInline
value class ProductName(
    val value: String,
) {
    init {
        BrandValidator.validateName(value)
    }
}
