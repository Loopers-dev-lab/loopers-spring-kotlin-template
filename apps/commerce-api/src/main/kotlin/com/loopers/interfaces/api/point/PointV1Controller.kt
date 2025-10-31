package com.loopers.interfaces.api.point

import com.loopers.application.member.MemberFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/points")
class PointV1Controller(
    private val memberFacade: MemberFacade,
) : PointV1ApiSpec {

    @GetMapping
    override fun getPoint(
        @RequestHeader("X-USER-ID") memberId: String
    ): ApiResponse<PointV1Dto.PointResponse> {  // ← 반환 타입 변경
        return memberFacade.getPoint(memberId)
            ?.let { PointV1Dto.PointResponse(it) }
            ?.let { ApiResponse.success(it) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다.")
    }

    @PostMapping("/charge")
    override fun chargePoint(
        @RequestHeader("X-USER-ID") memberId: String,
        @RequestBody request: PointV1Dto.ChargePointRequest
    ): ApiResponse<PointV1Dto.PointResponse> {
        val totalPoint = memberFacade.chargePoint(memberId, request.amount)
        return ApiResponse.success(PointV1Dto.PointResponse(totalPoint))
    }

}
