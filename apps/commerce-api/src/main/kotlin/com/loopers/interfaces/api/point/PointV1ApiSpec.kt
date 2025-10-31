package com.loopers.interfaces.api.point

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.config.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Point V1 API", description = "포인트 관련 API 입니다.")
interface PointV1ApiSpec {

    @Operation(
        summary = "포인트 조회",
        description = "사용자의 현재 포인트를 조회합니다. 포인트가 없는 경우 null을 반환합니다.",
    )
    fun getPoint(
        @Parameter(hidden = true)
        authUser: AuthUser,
    ): ApiResponse<PointResponse.PointResponseDto?>

    @Operation(
        summary = "포인트 충전",
        description = "사용자의 포인트를 충전합니다. 사용자가 존재하지 않으면 404를 반환합니다.",
    )
    fun chargePoint(
        @Parameter(hidden = true)
        authUser: AuthUser,

        @Schema(description = "포인트 충전 요청 객체")
        request: PointRequest.PointChargeRequestDto,
    ): ApiResponse<PointResponse.PointResponseDto>
}
