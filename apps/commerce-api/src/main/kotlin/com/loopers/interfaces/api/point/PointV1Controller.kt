package com.loopers.interfaces.api.point

import com.loopers.application.point.PointFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.config.AuthUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/points")
class PointV1Controller(
    private val pointFacade: PointFacade,
) : PointV1ApiSpec {

    @GetMapping
    override fun getPoint(
        authUser: AuthUser,
    ): ApiResponse<PointResponse.PointResponseDto?> {
        val result = pointFacade.findByUserId(authUser.userId)
        return result
            ?.let { PointResponse.PointResponseDto.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("/charge")
    override fun chargePoint(
        authUser: AuthUser,
        @Valid @RequestBody request: PointRequest.PointChargeRequestDto,
    ): ApiResponse<PointResponse.PointResponseDto> {
        val result = pointFacade.chargePoint(
            userId = authUser.userId,
            amount = request.amount,
        )
        return result
            .let { PointResponse.PointResponseDto.from(it) }
            .let { ApiResponse.success(it) }
    }
}
