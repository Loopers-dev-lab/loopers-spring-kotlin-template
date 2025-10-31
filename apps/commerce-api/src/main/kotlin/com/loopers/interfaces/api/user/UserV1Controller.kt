package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @GetMapping("/{userId}")
    override fun getUserBy(
        @PathVariable(value = "userId") userId: Long,
    ): ApiResponse<UserV1Response.GetUserById> {
        return userFacade.getUserBy(userId)
            .let { UserV1Response.GetUserById.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("/sign-up")
    override fun signUp(
        @RequestBody request: UserV1Request.SignUp,
    ): ApiResponse<UserV1Response.SignUp> {
        return userFacade.signUp(request.to())
            .let { UserV1Response.SignUp.from(it) }
            .let { ApiResponse.success(it) }
    }
}
