package com.loopers.domain.payment.policy

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.math.BigDecimal

object PaymentValidator {
    fun validatePrice(price: BigDecimal) {
        if (price.compareTo(PaymentPolicy.Price.MIN) < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, PaymentPolicy.Price.MESSAGE)
        }
    }
}
