package com.loopers.domain.point

object PointCommand {

    data class Charge(
        val amount: Long,
        val userId: String,
    )
}
