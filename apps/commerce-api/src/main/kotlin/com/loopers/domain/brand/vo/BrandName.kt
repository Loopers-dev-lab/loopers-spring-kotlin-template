package com.loopers.domain.brand.vo

import com.loopers.domain.brand.policy.BrandValidator

@JvmInline
value class BrandName(
    val value: String,
) {
    init {
        BrandValidator.validateName(value)
    }
}
