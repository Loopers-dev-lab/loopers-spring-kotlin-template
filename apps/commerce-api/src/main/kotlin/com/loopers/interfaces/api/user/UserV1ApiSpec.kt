package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "Loopers User API 입니다.")
interface UserV1ApiSpec {
    @Operation(
        summary = "유저 조회",
        description = "ID로 유저를 조회합니다.",
    )
    fun getUserBy(
        @Schema(name = "유저 ID", description = "조회할 유저의 ID")
        userId: Long,
    ): ApiResponse<UserV1Response.GetUserById>

    @Operation(
        summary = "유저 회원가입",
        description = "회원가입을 진행합니다.",
    )
    fun signUp(
        request: UserV1Request.SignUp,
    ): ApiResponse<UserV1Response.SignUp>
}
