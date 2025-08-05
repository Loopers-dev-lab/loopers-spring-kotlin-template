package com.loopers.domain.point.validation

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object PointValidator {
    fun validateMinAmount(point: Int) {
        if (PointPolicy.Min.VALUE >= point) {
            throw CoreException(ErrorType.BAD_REQUEST, PointPolicy.Min.MESSAGE)
        }
    }
}
