package com.loopers.domain.payment.policy

import java.math.BigDecimal

object PaymentPolicy {
    object Price {
        const val MESSAGE = "0원보다 작을 수 없습니다."
        val MIN = BigDecimal("0")
    }
}
