package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "유저 API")
interface UserApiSpec {
    @Operation(
        summary = "유저 회원가입",
        description = "로그인 아이디,성별,이메일,생년월일 정보를 토대로 회원가입",
    )
    fun registerUser(
        @Schema(description = "회원가입 요청 정보")
        request: UserRegisterDto.Request,
    ): ApiResponse<UserRegisterDto.Response>

    @Operation(
        summary = "회원 정보 조회",
        description = "로그인 아이디를 통해, 회원 정보 조회",
    )
    fun getUser(
        @Schema(description = "조회할 사용자의 로그인 아이디")
        loginId: String,
    ): ApiResponse<UserInfoDto.Response>
}
