package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @PostMapping
    override fun registerUser(
        @RequestBody @Valid request: UserV1Dto.RegisterRequest,
    ): ApiResponse<UserV1Dto.UserResponse> {
        val userInfo = userFacade.registerUser(request.toCommand())
        return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo))
    }

    @GetMapping("/me")
    override fun getMyInfo(
        @RequestHeader("X-USER-ID") userId: Long,
    ): ApiResponse<UserV1Dto.UserResponse> {
        val userInfo = userFacade.getUserInfo(userId)
        return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo))
    }
}
