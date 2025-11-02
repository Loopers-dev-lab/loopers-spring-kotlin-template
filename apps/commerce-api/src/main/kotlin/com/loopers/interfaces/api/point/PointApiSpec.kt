package com.loopers.interfaces.api.point

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Point V1 API", description = "포인트 API")
interface PointApiSpec {
    @Operation(
        summary = "포인트 조회",
        description = "유저 아이디로 포인트 조회",
    )
    fun getPoint(
        @Schema(description = "유저 로그인 아이디")
        loginId: String,
    ): ApiResponse<PointInfoDto.Response>

    @Operation(
        summary = "포인트 충전",
        description = "유저 아이디로 포인트 충전",
    )
    fun chargePoint(
        @Schema(description = "유저 로그인 아이디")
        loginId: String,
        @Schema(description = "포인트 충전할 총량")
        request: PointChargeDto.Request,
    ): ApiResponse<PointChargeDto.Response>
}
