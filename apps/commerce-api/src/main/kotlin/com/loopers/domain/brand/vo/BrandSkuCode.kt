package com.loopers.domain.brand.vo

import com.loopers.domain.brand.policy.BrandSkuValidator

@JvmInline
value class BrandSkuCode(val code: String) {
    init {
        BrandSkuValidator.validateSkuCode(code)
    }
}
