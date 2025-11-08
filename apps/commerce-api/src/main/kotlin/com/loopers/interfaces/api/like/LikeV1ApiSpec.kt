package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page

@Tag(name = "Like V1 API", description = "좋아요 API")
interface LikeV1ApiSpec {
    @Operation(
        summary = "상품 좋아요 등록",
        description = "상품에 좋아요를 등록합니다. 멱등성을 보장합니다."
    )
    fun addLike(
        @Parameter(description = "사용자 ID", required = true)
        userId: Long,
        @Schema(description = "상품 ID")
        productId: Long
    ): ApiResponse<Unit>

    @Operation(
        summary = "상품 좋아요 취소",
        description = "상품 좋아요를 취소합니다. 멱등성을 보장합니다."
    )
    fun removeLike(
        @Parameter(description = "사용자 ID", required = true)
        userId: Long,
        @Schema(description = "상품 ID")
        productId: Long
    ): ApiResponse<Unit>

    @Operation(
        summary = "좋아요한 상품 목록 조회",
        description = "사용자가 좋아요한 상품 목록을 조회합니다."
    )
    fun getLikedProducts(
        @Parameter(description = "사용자 ID", required = true)
        userId: Long,
        @Parameter(description = "페이지 번호", schema = Schema(defaultValue = "0"))
        page: Int,
        @Parameter(description = "페이지 크기", schema = Schema(defaultValue = "20"))
        size: Int
    ): ApiResponse<Page<LikeV1Dto.LikedProductResponse>>
}
