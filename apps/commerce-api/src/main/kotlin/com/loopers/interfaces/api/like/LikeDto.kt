package com.loopers.interfaces.api.like

import jakarta.validation.constraints.Positive

class LikeDto {

    data class Request(
        @field:Positive
        val userId: Long,

        @field:Positive
        val productId: Long,
    )

    data class Response(val isSuccess: Boolean) {
        companion object {
            fun success() = Response(true)
        }
    }
}
