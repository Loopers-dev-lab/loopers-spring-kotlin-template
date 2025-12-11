package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Like", description = "좋아요 API")
interface LikeApiSpec {

    @Operation(summary = "좋아요 등록 API", description = "상품에 대해 좋아요를 등록한다.")
    fun like(
        @Schema(description = "좋아요 한 유저와 좋아요 한 상품 정보")
        request: LikeDto.Request,
    ): ApiResponse<LikeDto.Response>

    @Operation(summary = "좋아요 취소 API", description = "상품에 대해 좋아요를 취소한다.")
    fun unlike(
        @Schema(description = "좋아요 취소 한 유저와 좋아요 취소한 상품 정보")
        request: LikeDto.Request,
    ): ApiResponse<LikeDto.Response>
}
