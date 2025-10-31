package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.domain.user.Gender
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
    @PostMapping("/register")
    override fun registerUser(@RequestBody req: UserV1Dto.RegisterUserRequest): ApiResponse<UserV1Dto.UserResponse> {
        if (req.gender == Gender.NONE) throw CoreException(ErrorType.BAD_REQUEST)

        return userFacade.registerUser(req.userId, req.email, req.birth, req.gender)
            .let { UserV1Dto.UserResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    override fun getUser(
        @PathVariable userId: String,
    ): ApiResponse<UserV1Dto.UserResponse> {
        return userFacade.getUser(userId)
            .let { UserV1Dto.UserResponse.from(it) }
            .let { ApiResponse.success(it) }
    }
}
