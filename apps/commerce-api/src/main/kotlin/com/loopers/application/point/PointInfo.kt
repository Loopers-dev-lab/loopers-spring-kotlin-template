package com.loopers.application.point

import java.math.BigDecimal

data class PointInfo(val balance: BigDecimal) {
    companion object {
        fun from(balance: BigDecimal) = PointInfo(
            balance = balance,
        )
    }
}
