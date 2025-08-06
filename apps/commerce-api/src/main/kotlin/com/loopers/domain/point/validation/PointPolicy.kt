package com.loopers.domain.point.validation

import java.math.BigDecimal

object PointPolicy {
    object Point {
        val MIN_VALUE = BigDecimal("0")
        const val MESSAGE = "0 이하의 정수가 들어갈 수 없습니다."
    }
}
