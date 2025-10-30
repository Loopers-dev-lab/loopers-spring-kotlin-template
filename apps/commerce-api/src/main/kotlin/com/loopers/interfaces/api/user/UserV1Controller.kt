package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/sign-up")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @PostMapping("")
    override fun signUp(
        @RequestBody request: UserV1Request.SignUp,
    ): ApiResponse<UserV1Response.SignUp> {
        return userFacade.signUp(request.to())
            .let { UserV1Response.SignUp.from(it) }
            .let { ApiResponse.success(it) }
    }
}
