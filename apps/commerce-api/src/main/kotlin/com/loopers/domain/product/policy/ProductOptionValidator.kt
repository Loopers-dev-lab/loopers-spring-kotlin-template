package com.loopers.domain.product.policy

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.math.BigDecimal

object ProductOptionValidator {

    fun validateDisplayName(name: String) {
        if (!Regex(ProductOptionPolicy.DisplayName.PATTERN).matches(name)) {
            throw CoreException(ErrorType.BAD_REQUEST, ProductOptionPolicy.DisplayName.MESSAGE)
        }
    }

    fun validateColor(color: String) {
        if (!Regex(ProductOptionPolicy.Color.PATTERN).matches(color)) {
            throw CoreException(ErrorType.BAD_REQUEST, ProductOptionPolicy.Color.MESSAGE)
        }
    }

    fun validateSize(size: String) {
        if (!Regex(ProductOptionPolicy.Size.PATTERN).matches(size)) {
            throw CoreException(ErrorType.BAD_REQUEST, ProductOptionPolicy.Size.MESSAGE)
        }
    }

    fun validateAdditionalPrice(additionalPrice: BigDecimal) {
        if (additionalPrice.compareTo(ProductOptionPolicy.AdditionalPrice.MIN) < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, ProductOptionPolicy.AdditionalPrice.MESSAGE)
        }
    }
}
