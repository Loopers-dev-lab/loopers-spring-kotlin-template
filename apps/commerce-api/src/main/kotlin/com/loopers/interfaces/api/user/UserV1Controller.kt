package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
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
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
    private val authContext: AuthContext,
) : UserV1ApiSpec {

    @Authenticated
    @GetMapping("/me")
    override fun getMe(): ApiResponse<UserV1Dto.UserResponse> {
        return userFacade.getMe(authContext.getUserName().value)
            .let { UserV1Dto.UserResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("")
    override fun signUp(
        @Valid @RequestBody request: UserV1Dto.UserSignUpRequest,
    ): ApiResponse<UserV1Dto.UserResponse> {
        return userFacade.signUp(request.toUserSignUp())
            .let { UserV1Dto.UserResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
