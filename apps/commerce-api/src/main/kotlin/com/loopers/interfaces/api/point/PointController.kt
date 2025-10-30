package com.loopers.interfaces.api.point

import com.loopers.application.point.PointFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/points")
class PointController(private val pointFacade: PointFacade) : PointApiSpec {

    @GetMapping
    override fun getPoint(
        @RequestHeader("X-USER-ID", required = true) loginId: String,
    ): ApiResponse<PointInfoDto.Response> =
        pointFacade.getPointByUserId(loginId)
            .let { PointInfoDto.Response.from(it) }
            .let { ApiResponse.success(it) }

    @PostMapping
    override fun chargePoint(
        @RequestHeader("X-USER-ID", required = true) loginId: String,
        @RequestBody request: PointChargeDto.Request,
    ): ApiResponse<PointChargeDto.Response> =
        pointFacade.charge(loginId, request.balance)
            .let { PointChargeDto.Response.from(it) }
            .let { ApiResponse.success(it) }
}
