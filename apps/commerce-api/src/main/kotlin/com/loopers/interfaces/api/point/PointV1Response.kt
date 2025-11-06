package com.loopers.interfaces.api.point

import com.loopers.application.point.PointV1Info

class PointV1Response {
    data class Charge(
        val balance: Int,
    ) {
        companion object {
            fun from(info: PointV1Info.Charge): Charge {
                return Charge(
                    balance = info.balance,
                )
            }
        }
    }

    data class GetBalance(
        val balance: Int,
    ) {
        companion object {
            fun from(info: PointV1Info.GetBalance): GetBalance {
                return GetBalance(
                    balance = info.balance,
                )
            }
        }
    }
}
