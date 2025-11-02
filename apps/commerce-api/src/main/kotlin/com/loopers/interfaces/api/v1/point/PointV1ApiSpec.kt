package com.loopers.interfaces.api.v1.point

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Point V1 API", description = "포인트 API")
interface PointV1ApiSpec {
    @Operation(
        summary = "포인트 충전",
        description = "포인트를 충전한다.",
    )
    fun charge(
        @Schema(name = "포인트 충전", description = "포인트를 충전한다")
        request: PointV1Request.Charge,
    ): ApiResponse<PointV1Response.Charge>

    @Operation(
        summary = "포인트 조회",
        description = "포인트를 조회한다.",
    )
    fun get(
        @Parameter(
            name = "X-USER-ID",
            description = "사용자 ID (헤더로 전달)",
            required = true,
            example = "user123",
            schema = Schema(type = "string"),
        )
        userId: String,
    ): ApiResponse<PointV1Response.Get>
}
