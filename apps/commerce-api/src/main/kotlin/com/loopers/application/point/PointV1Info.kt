package com.loopers.application.point

import com.loopers.domain.point.Money

class PointV1Info {
    data class Charge(
        val balance: Int,
    ) {
        companion object {
            fun from(amount: Money): Charge {
                return Charge(
                    balance = amount.amount.toInt(),
                )
            }
        }
    }

    data class GetBalance(
        val balance: Int,
    ) {
        companion object {
            fun from(amount: Money): GetBalance {
                return GetBalance(
                    balance = amount.amount.toInt(),
                )
            }
        }
    }
}
