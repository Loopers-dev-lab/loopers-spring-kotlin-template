package com.loopers.interfaces.api.v1.like

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.dto.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Like V1 API", description = "좋아요 API")
interface LikeV1ApiSpec {
    @Operation(
        summary = "상품 좋아요 등록",
        description = "상품에 좋아요를 등록합니다. 이미 좋아요한 경우 멱등하게 동작합니다.",
    )
    fun likeProduct(
        @Schema(description = "상품 ID")
        productId: Long,
        @Parameter(description = "사용자 ID (X-USER-ID 헤더)", required = true)
        userId: String,
    ): ApiResponse<Unit>

    @Operation(
        summary = "상품 좋아요 취소",
        description = "상품 좋아요를 취소합니다. 이미 취소된 경우 멱등하게 동작합니다.",
    )
    fun unlikeProduct(
        @Schema(description = "상품 ID")
        productId: Long,
        @Parameter(description = "사용자 ID (X-USER-ID 헤더)", required = true)
        userId: String,
    ): ApiResponse<Unit>

    @Operation(
        summary = "내가 좋아요한 상품 목록 조회",
        description = "사용자가 좋아요한 상품 목록을 최신순으로 조회합니다.",
    )
    fun getLikedProducts(
        @Parameter(description = "사용자 ID (X-USER-ID 헤더)", required = true)
        userId: String,
        @Parameter(description = "페이지 번호 (0부터 시작)", schema = Schema(defaultValue = "0"))
        pageable: Pageable,
    ): ApiResponse<PageResponse<LikeV1Dto.LikedProductListResponse>>
}
