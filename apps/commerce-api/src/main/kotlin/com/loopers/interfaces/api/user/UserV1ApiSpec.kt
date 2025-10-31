package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "회원 API")
interface UserV1ApiSpec {
    @Operation(
        summary = "회원 가입",
        description = "ID, email, birth, gender 정보로 회원 가입 시도",
    )
    fun registerUser(req: UserV1Dto.RegisterUserRequest): ApiResponse<UserV1Dto.UserResponse>

    @Operation(
        summary = "회원 조회",
        description = "ID로 회원을 조회합니다.",
    )
    fun getUser(
        @Schema(name = "회원 ID", description = "조회할 회원의 ID")
        userId: String,
    ): ApiResponse<UserV1Dto.UserResponse>
}
