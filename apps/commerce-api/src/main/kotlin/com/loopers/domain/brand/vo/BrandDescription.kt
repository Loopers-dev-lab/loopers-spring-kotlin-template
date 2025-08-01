package com.loopers.domain.brand.vo

import com.loopers.domain.brand.policy.BrandValidator

@JvmInline
value class BrandDescription(
    val value: String,
) {
    init {
        BrandValidator.validateDescription(value)
    }
}
