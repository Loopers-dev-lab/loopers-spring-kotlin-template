package com.loopers.interfaces.api.point

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Point V1 API", description = "포인트 관련 API 입니다.")
interface PointV1ApiSpec {
    @Operation(
        summary = "포인트 조회",
        description = "ID로 포인트를 조회합니다.",
    )
    fun getPoint(
        @Schema(name = "회원 ID", description = "조회할 회원의 ID")
        memberId: String,
    ): ApiResponse<PointV1Dto.PointResponse>

    @Operation(
        summary = "포인트 충전",
        description = "ID와 금액으로 포인트를 충전합니다.",
    )
    fun chargePoint(
        @Schema(name = "회원 ID", description = "조회할 회원의 ID")
        memberId: String,
        @Schema(name = "충전 포인트 금액", description = "충전할 포인트 금액")
        request: PointV1Dto.ChargePointRequest
    ): ApiResponse<PointV1Dto.PointResponse>
}
