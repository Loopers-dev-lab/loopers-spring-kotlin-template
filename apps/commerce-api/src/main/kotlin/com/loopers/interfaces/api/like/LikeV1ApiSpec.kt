package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@Tag(name = "Like V1 API", description = "좋아요 관련 API 입니다.")
interface LikeV1ApiSpec {

    @Operation(
        summary = "상품 좋아요",
        description = "상품에 좋아요를 등록합니다. 멱등성을 보장합니다.",
    )
    fun likeProduct(
        @Schema(name = "회원 ID", description = "좋아요 등록하는 회원 ID")
        memberId: String,
        @Schema(name = "상품 ID", description = "좋아요를 등록할 상품 ID")
        productId: Long,
    ): ApiResponse<LikeV1Dto.LikeResponse>

    @Operation(
        summary = "좋아요 취소",
        description = "상품 좋아요를 취소합니다. 멱등성을 보장합니다.",
    )
    fun cancelLike(
        @Schema(name = "회원 ID", description = "좋아요 취소하는 회원 ID")
        memberId: String,
        @Schema(name = "상품 ID", description = "좋아요 취소할 상품 ID")
        productId: Long,
    ): ApiResponse<Unit>

    @Operation(
        summary = "내 좋아요 목록 조회",
        description = "회원의 좋아요 목록을 페이징하여 조회합니다.",
    )
    fun getMyLikes(
        @Schema(name = "회원 ID", description = "조회하는 회원 ID")
        memberId: String,
        @Schema(name = "페이지 정보", description = "페이지 번호와 크기")
        pageable: Pageable,
    ): ApiResponse<Page<LikeV1Dto.LikeResponse>>
}
