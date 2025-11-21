package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.config.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Like V1 API", description = "좋아요 관련 API 입니다.")
interface LikeV1ApiSpec {

    @Operation(summary = "상품 좋아요 추가")
    fun addLike(
        authUser: AuthUser,
        request: LikeRequest.AddDto,
    ): ApiResponse<Unit>

    @Operation(summary = "상품 좋아요 취소")
    fun removeLike(
        authUser: AuthUser,
        request: LikeRequest.RemoveDto,
    ): ApiResponse<Unit>
}
