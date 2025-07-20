package com.loopers.interfaces.api.point

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "User API 입니다.")
interface PointV1ApiSpec {
    @Operation(
        summary = "내 포인트 조회",
        description = "내 포인트를 조회합니다.",
    )
    fun getMe(): ApiResponse<PointV1Dto.PointResponse>

    @Operation(
        summary = "회원 가입",
        description = "회원 가입 합니다.",
    )
    fun charge(
        @Schema(name = "충전 정보", description = "충전 정보")
        request: PointV1Dto.ChargeRequest,
    ): ApiResponse<PointV1Dto.PointResponse>
}
