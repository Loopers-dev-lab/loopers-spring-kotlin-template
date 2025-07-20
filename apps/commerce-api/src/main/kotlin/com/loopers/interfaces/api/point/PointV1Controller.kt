package com.loopers.interfaces.api.point

import com.loopers.application.point.PointFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.annotation.Authenticated
import com.loopers.support.auth.context.AuthContext
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
    private val authContext: AuthContext,
) : PointV1ApiSpec {

    @Authenticated
    @GetMapping("")
    override fun getMe(): ApiResponse<PointV1Dto.PointResponse> {
        return pointFacade.getMe(authContext.getId())
            .let { PointV1Dto.PointResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @Authenticated
    @PostMapping("/charge")
    override fun charge(
        @Valid @RequestBody request: PointV1Dto.ChargeRequest,
    ): ApiResponse<PointV1Dto.PointResponse> {
        return pointFacade.charge(request.toCharge(authContext.getUserName().value))
            .let { PointV1Dto.PointResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
