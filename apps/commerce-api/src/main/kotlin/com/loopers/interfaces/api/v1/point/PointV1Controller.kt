package com.loopers.interfaces.api.v1.point

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

    @PostMapping
    override fun charge(
        @RequestBody @Valid request: PointV1Request.Charge,
    ): ApiResponse<PointV1Response.Charge> {
        return pointFacade.charge(request.toCommand())
            .let { PointV1Response.Charge.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    override fun get(
        @RequestHeader("X-USER-ID") userId: String,
    ): ApiResponse<PointV1Response.Get> {
        return pointFacade.getBy(userId)
            .let { PointV1Response.Get.from(it) }
            .let { ApiResponse.success(it) }
    }
}
