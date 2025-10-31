package com.loopers.interfaces.api.v1.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "유저 API")
interface UserV1ApiSpec {
    @Operation(
        summary = "회원가입",
        description = "유저가 회원가입을 한다.",
    )
    fun signUp(
        @Schema(name = "회원가입 정보", description = "회원가입할 정보")
        request: UserV1Request.SignUp,
    ): ApiResponse<UserV1Response.SignUp>

    @Operation(
        summary = "내 정보 조회",
        description = "내 정보를 조회한다.",
    )
    fun getMyInfo(
        @Parameter(
            name = "X-USER-ID",
            description = "사용자 ID (헤더로 전달)",
            required = true,
            example = "user123",
            schema = Schema(type = "string"),
        )
        userId: String,
    ): ApiResponse<UserV1Response.MyInfo>
}
