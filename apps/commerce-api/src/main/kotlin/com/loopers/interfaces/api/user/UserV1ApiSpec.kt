package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "User API 입니다.")
interface UserV1ApiSpec {
    @Operation(
        summary = "내 유저 조회",
        description = "ID로 내 유저를 조회합니다.",
    )
    fun getMe(): ApiResponse<UserV1Dto.UserResponse>

    @Operation(
        summary = "회원 가입",
        description = "회원 가입 합니다.",
    )
    fun signUp(
        @Schema(name = "유저 정보", description = "회원 가입할 유저 정보")
        request: UserV1Dto.UserSignUpRequest,
    ): ApiResponse<UserV1Dto.UserResponse>
}
