package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @PostMapping("/users")
    override fun registerUser(
        @RequestBody request: UserV1Dto.RegisterUserRequest,
    ): ApiResponse<UserV1Dto.UserResponse> {
        return userFacade.registerUser(
            userId = request.userId,
            email = request.email,
            birthDate = request.birthDate,
            gender = request.gender,
        )
            .let { UserV1Dto.UserResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/users/{userId}")
    override fun getUserByUserId(
        @PathVariable userId: String,
    ): ApiResponse<UserV1Dto.UserResponse> {
        return userFacade.getUserByUserId(userId)
            .let { UserV1Dto.UserResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/points")
    override fun getPoint(
        @RequestHeader("X-USER-ID", required = false) userId: String?,
    ): ApiResponse<UserV1Dto.PointResponse> {
        if (userId.isNullOrBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "X-USER-ID 헤더가 필요합니다.")
        }

        return userFacade.getPointByUserId(userId)
            .let { UserV1Dto.PointResponse(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("/points/charge")
    override fun chargePoint(
        @RequestHeader("X-USER-ID", required = false) userId: String?,
        @RequestBody request: UserV1Dto.ChargePointRequest,
    ): ApiResponse<UserV1Dto.PointResponse> {
        if (userId.isNullOrBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "X-USER-ID 헤더가 필요합니다.")
        }

        return userFacade.chargePoint(userId, request.amount)
            .let { UserV1Dto.PointResponse(it) }
            .let { ApiResponse.success(it) }
    }
}
