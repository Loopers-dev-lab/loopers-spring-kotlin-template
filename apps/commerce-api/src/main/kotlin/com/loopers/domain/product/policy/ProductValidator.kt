package com.loopers.domain.product.policy

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.math.BigDecimal

object ProductValidator {

    fun validateName(name: String) {
        if (!Regex(ProductPolicy.Name.PATTERN).matches(name)) {
            throw CoreException(ErrorType.BAD_REQUEST, ProductPolicy.Name.MESSAGE)
        }
    }

    fun validateDescription(description: String) {
        if (!Regex(ProductPolicy.Description.PATTERN).matches(description)) {
            throw CoreException(ErrorType.BAD_REQUEST, ProductPolicy.Description.MESSAGE)
        }
    }

    fun validatePrice(price: BigDecimal) {
        if (price.compareTo(ProductPolicy.Price.MIN) < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, ProductPolicy.Price.MESSAGE)
        }
    }
}
