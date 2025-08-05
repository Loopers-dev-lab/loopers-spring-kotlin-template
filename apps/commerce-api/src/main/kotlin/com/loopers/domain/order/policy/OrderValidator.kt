package com.loopers.domain.order.policy

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.math.BigDecimal

object OrderValidator {
    fun validateFinalPrice(price: BigDecimal) {
        if (price.compareTo(OrderPolicy.FinalPrice.MIN) < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, OrderPolicy.FinalPrice.MESSAGE)
        }
    }

    fun validateOriginalPrice(price: BigDecimal) {
        if (price.compareTo(OrderPolicy.OriginalPrice.MIN) < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, OrderPolicy.OriginalPrice.MESSAGE)
        }
    }
}
