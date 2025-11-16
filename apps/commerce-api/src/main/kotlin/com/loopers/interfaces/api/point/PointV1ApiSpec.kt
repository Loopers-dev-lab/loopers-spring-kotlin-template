package com.loopers.interfaces.api.point

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Point V1 API", description = "포인트 API")
interface PointV1ApiSpec {
    @Operation(
        summary = "포인트 충전",
        description = "사용자의 포인트를 충전합니다.",
    )
    fun chargePoint(
        @Parameter(description = "사용자 ID", required = true)
        userId: Long,
        request: PointV1Dto.ChargeRequest,
    ): ApiResponse<PointV1Dto.PointResponse>

    @Operation(
        summary = "보유 포인트 조회",
        description = "사용자의 현재 포인트 잔액을 조회합니다.",
    )
    fun getPoint(
        @Parameter(description = "사용자 ID", required = true)
        userId: Long,
    ): ApiResponse<PointV1Dto.PointResponse>
}
