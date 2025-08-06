package com.loopers.domain.point.validation

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.math.BigDecimal

object PointValidator {
    fun validateMinAmount(point: BigDecimal) {
        if (point.compareTo(PointPolicy.Point.MIN_VALUE) <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, PointPolicy.Point.MESSAGE)
        }
    }
}
