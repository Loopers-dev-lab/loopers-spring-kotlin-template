package com.loopers.interfaces.api.point

import com.loopers.application.point.PointFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.values.Money
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
        @RequestBody request: PointV1Request.Charge,
    ): ApiResponse<PointV1Response.Charge> {
        return pointFacade.charge(userId, Money.krw(request.amount))
            .let { PointV1Response.Charge.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    override fun getPointBalance(
        @RequestHeader("X-USER-ID") userId: Long,
    ): ApiResponse<PointV1Response.GetBalance> {
        return pointFacade.getBalance(userId)
            .let { PointV1Response.GetBalance.from(it) }
            .let { ApiResponse.success(it) }
    }
}
