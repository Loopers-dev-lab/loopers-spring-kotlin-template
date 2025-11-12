package com.loopers.interfaces.api.like

import com.loopers.domain.like.Like
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

sealed class LikeResponse {

    data class LikeInfoDto(
        @get:Schema(description = "사용자 ID", example = "1")
        val userId: Long,

        @get:Schema(description = "상품 ID", example = "1")
        val productId: Long,

        @get:Schema(description = "좋아요 생성일시", example = "2024-01-01T00:00:00+09:00")
        val createdAt: ZonedDateTime,
    ) {
        companion object {
            fun from(like: Like): LikeInfoDto {
                return LikeInfoDto(
                    userId = like.userId,
                    productId = like.productId,
                    createdAt = like.createdAt,
                )
            }
        }
    }
}
