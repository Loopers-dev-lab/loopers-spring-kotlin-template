package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "사용자 API")
interface UserV1ApiSpec {
    @Operation(
        summary = "회원가입",
        description = "신규 사용자를 등록합니다.",
    )
    fun registerUser(
        request: UserV1Dto.RegisterRequest,
    ): ApiResponse<UserV1Dto.UserResponse>

    @Operation(
        summary = "내 정보 조회",
        description = "현재 로그인한 사용자의 정보를 조회합니다.",
    )
    fun getMyInfo(
        @Parameter(description = "사용자 ID", required = true)
        userId: Long,
    ): ApiResponse<UserV1Dto.UserResponse>
}
