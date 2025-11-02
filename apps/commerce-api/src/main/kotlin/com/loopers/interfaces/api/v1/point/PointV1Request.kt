package com.loopers.interfaces.api.v1.point

import com.loopers.domain.point.PointCommand
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

object PointV1Request {

    data class Charge(
        @field:NotBlank
        val userId: String,

        @field:NotNull
        @field:Min(1)
        val amount: Long,
    ) {
        fun toCommand() = PointCommand.Charge(
            userId = userId,
            amount = amount,
        )
    }
}
