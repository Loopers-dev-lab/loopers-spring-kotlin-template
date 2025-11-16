package com.loopers.interfaces.api.point

import com.loopers.application.point.PointFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/points")
class PointV1Controller(
    private val pointFacade: PointFacade,
) : PointV1ApiSpec {
    @PostMapping("/charge")
    override fun chargePoint(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestBody @Valid request: PointV1Dto.ChargeRequest,
    ): ApiResponse<PointV1Dto.PointResponse> {
        val pointInfo = pointFacade.chargePoint(userId, request.toCommand())
        return ApiResponse.success(PointV1Dto.PointResponse.from(pointInfo))
    }

    @GetMapping
    override fun getPoint(
        @RequestHeader("X-USER-ID") userId: Long,
    ): ApiResponse<PointV1Dto.PointResponse> {
        val pointInfo = pointFacade.getPoint(userId)
        return ApiResponse.success(PointV1Dto.PointResponse.from(pointInfo))
    }
}
