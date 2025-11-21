package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Like V1 API", description = "Loopers Product Like API 입니다.")
interface LikeV1ApiSpec {
    @Operation(
        summary = "상품 좋아요 추가",
        description = "상품에 좋아요를 추가합니다. 이미 좋아요한 상품이면 중복 추가되지 않습니다 (멱등성).",
    )
    fun addLike(
        @Parameter(
            name = "X-USER-ID",
            description = "요청자의 유저 ID",
            required = true,
            `in` = ParameterIn.HEADER,
        )
        userId: Long,
        @Parameter(
            name = "productId",
            description = "좋아요할 상품 ID",
            required = true,
            `in` = ParameterIn.PATH,
        )
        productId: Long,
    ): ApiResponse<Unit>

    @Operation(
        summary = "상품 좋아요 삭제",
        description = "상품의 좋아요를 삭제합니다. 좋아요하지 않은 상품이어도 에러가 발생하지 않습니다 (멱등성).",
    )
    fun removeLike(
        @Parameter(
            name = "X-USER-ID",
            description = "요청자의 유저 ID",
            required = true,
            `in` = ParameterIn.HEADER,
        )
        userId: Long,
        @Parameter(
            name = "productId",
            description = "좋아요 삭제할 상품 ID",
            required = true,
            `in` = ParameterIn.PATH,
        )
        productId: Long,
    ): ApiResponse<Unit>
}
