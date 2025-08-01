package com.loopers.domain.order.policy

import java.math.BigDecimal

object OrderPolicy {
    object FinalPrice {
        const val MESSAGE = "0원보다 작을 수 없습니다."
        val MIN = BigDecimal("0")
    }

    object OriginalPrice {
        const val MESSAGE = "0원보다 작을 수 없습니다."
        val MIN = BigDecimal("0")
    }
}
