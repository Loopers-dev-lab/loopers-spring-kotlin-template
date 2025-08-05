package com.loopers.domain.product.policy

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object ProductStockValidator {

    fun validateQuantity(quantity: Int) {
        if (ProductStockPolicy.Quantity.MIN > quantity) {
            throw CoreException(ErrorType.BAD_REQUEST, ProductStockPolicy.Quantity.MESSAGE)
        }
    }
}
