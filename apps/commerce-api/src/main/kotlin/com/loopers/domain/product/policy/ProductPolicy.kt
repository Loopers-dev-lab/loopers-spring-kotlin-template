package com.loopers.domain.product.policy

import java.math.BigDecimal

object ProductPolicy {
    object Name {
        const val MESSAGE = "영문 및 숫자 및 한글 100자 이내"
        const val PATTERN = "^[a-zA-Z0-9가-힣]{1,100}\$"
    }

    object Description {
        const val MESSAGE = "영문 및 숫자 및 한글 100자 이내"
        const val PATTERN = "^[a-zA-Z0-9가-힣]{1,100}\$"
    }

    object Price {
        const val MESSAGE = "0원보다 작을 수 없습니다."
        val MIN = BigDecimal("0")
    }
}
