package com.loopers.interfaces.api.point

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Point V1 API", description = "Loopers Point API 입니다.")
interface PointV1ApiSpec {
    @Operation(
        summary = "포인트 충전",
        description = "포인트를 충전합니다.",
    )
    fun chargePoint(
        @Parameter(
            name = "X-USER-ID",
            description = "요청자의 유저 id",
            required = true,
            `in` = ParameterIn.HEADER,
        )
        userId: Long,
        request: PointV1Request.Charge,
    ): ApiResponse<PointV1Response.Charge>
}
