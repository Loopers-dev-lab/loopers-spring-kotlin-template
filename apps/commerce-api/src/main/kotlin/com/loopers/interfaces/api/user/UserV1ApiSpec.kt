package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "User 관련 API 입니다.")
interface UserV1ApiSpec {
    @Operation(
        summary = "회원가입",
    )
    fun createUser(
        @Schema(description = "회원가입 요청 객체")
        userCreateRequestDto: UserRequest.UserCreateRequestDto,
    ): ApiResponse<UserResponse.UserResponseDto>

    @Operation(
        summary = "회원조회",
    )
    fun getUser(
        @Schema(description = "로그인 아이디")
        username: String,
    ): ApiResponse<UserResponse.UserResponseDto>
}
