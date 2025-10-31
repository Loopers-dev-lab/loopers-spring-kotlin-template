package com.loopers.interfaces.api.v1.user

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
    override fun signUp(
        @RequestBody @Valid request: UserV1Request.SignUp,
    ): ApiResponse<UserV1Response.SignUp> {
        return userFacade.signUp(request.toCommand())
            .let { UserV1Response.SignUp.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/me")
    override fun getMyInfo(
        @RequestHeader("X-USER-ID") userId: String,
    ): ApiResponse<UserV1Response.MyInfo> {
        return userFacade.getMyInfo(userId)
            .let { UserV1Response.MyInfo.from(it) }
            .let { ApiResponse.success(it) }
    }
}
