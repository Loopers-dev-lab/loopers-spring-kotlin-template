package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
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
) : UserV1ApiSpec {

    @PostMapping
    override fun createUser(
        @Valid @RequestBody request: UserRequest.UserCreateRequestDto,
    ): ApiResponse<UserResponse.UserResponseDto> {
        val result = userFacade.createUser(
            username = request.username,
            password = request.password,
            email = request.email,
            birthDate = request.birthDate,
            gender = request.gender,
        )
        return result
            .let { UserResponse.UserResponseDto.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping
    override fun getUser(
        username: String,
    ): ApiResponse<UserResponse.UserResponseDto> {
        val result = userFacade.getUserByUsername(username)
        return result
            .let { UserResponse.UserResponseDto.from(it) }
            .let { ApiResponse.success(it) }
    }
}
