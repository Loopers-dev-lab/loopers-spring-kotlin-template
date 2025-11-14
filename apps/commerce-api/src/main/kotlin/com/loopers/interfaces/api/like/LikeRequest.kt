package com.loopers.interfaces.api.like

import io.swagger.v3.oas.annotations.media.Schema

sealed class LikeRequest {

    data class AddDto(
        @get:Schema(description = "상품 ID", example = "1", required = true)
        val productId: Long,
    )

    data class RemoveDto(
        @get:Schema(description = "상품 ID", example = "1", required = true)
        val productId: Long,
    )
}
