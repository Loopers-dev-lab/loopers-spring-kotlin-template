package com.loopers.application.point

import com.loopers.domain.point.PointAccount

class PointV1Info {
    data class Charge(
        val balance: Int,
    ) {
        companion object {
            fun from(pointAccount: PointAccount): Charge {
                return Charge(
                    balance = pointAccount.balance.amount.toInt(),
                )
            }
        }
    }

    data class GetBalance(
        val balance: Int,
    ) {
        companion object {
            fun from(pointAccount: PointAccount): GetBalance {
                return GetBalance(
                    balance = pointAccount.balance.amount.toInt(),
                )
            }
        }
    }
}
