package com.loopers.interfaces.api.point

import com.loopers.application.point.PointInfo

class PointInfoDto {
    data class Response(val balance: Long) {
        companion object {
            fun from(info: PointInfo): Response = Response(
                balance = info.balance,
            )
        }
    }
}
