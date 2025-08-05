package com.loopers.domain.product.vo

import com.loopers.domain.brand.policy.BrandValidator

@JvmInline
value class ProductDescription(
    val value: String,
) {
    init {
        BrandValidator.validateDescription(value)
    }
}
