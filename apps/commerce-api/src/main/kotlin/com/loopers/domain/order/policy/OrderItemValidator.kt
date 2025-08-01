package com.loopers.domain.order.policy

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object OrderItemValidator {
    fun validateQuantity(quantity: Int) {
        if (quantity.compareTo(OrderItemPolicy.Quantity.MIN) < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, OrderItemPolicy.Quantity.MESSAGE)
        }
    }
}
