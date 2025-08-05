package com.loopers.domain.product.vo

import com.loopers.domain.product.policy.ProductValidator
import java.math.BigDecimal

@JvmInline
value class ProductPrice(
    val value: BigDecimal,
) {
    init {
        ProductValidator.validatePrice(value)
    }
}
