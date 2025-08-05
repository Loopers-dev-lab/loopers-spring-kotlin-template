package com.loopers.domain.product.vo

import com.loopers.domain.product.policy.ProductOptionValidator
import java.math.BigDecimal

@JvmInline
value class ProductOptionAdditionalPrice(
    val value: BigDecimal,
) {
    init {
        ProductOptionValidator.validateAdditionalPrice(value)
    }
}
