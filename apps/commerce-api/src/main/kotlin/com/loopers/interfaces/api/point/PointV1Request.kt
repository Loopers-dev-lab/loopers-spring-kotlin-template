package com.loopers.interfaces.api.point

import io.swagger.v3.oas.annotations.media.Schema

class PointV1Request {
    data class Charge(
        @field:Schema(
            description = "충전할 포인트 수 (양수)",
        )
        val amount: Int,
    )
}
