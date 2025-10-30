package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.application.user.UserRegisterCommand
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userFacade: UserFacade) : UserApiSpec {

    @PostMapping
    override fun registerUser(
        @Valid @RequestBody request: UserRegisterDto.Request,
    ): ApiResponse<UserRegisterDto.Response> {
        val command = UserRegisterCommand(
            loginId = request.loginId,
            email = request.email,
            birthDate = request.birthDate,
            gender = request.gender,
        )

        return userFacade.registerUser(command)
            .let { UserRegisterDto.Response.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/{loginId}")
    override fun getUser(
        @PathVariable loginId: String,
    ): ApiResponse<UserInfoDto.Response> = userFacade.getUser(loginId)
        .let { UserInfoDto.Response.from(it) }
        .let { ApiResponse.success(it) }
}
