package com.loopers.application.point

data class PointInfo(val balance: Long) {
    companion object {
        fun from(balance: Long) = PointInfo(
            balance = balance,
        )
    }
}
