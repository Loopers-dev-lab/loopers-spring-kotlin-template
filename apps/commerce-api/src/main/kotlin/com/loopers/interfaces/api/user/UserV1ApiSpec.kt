package com.loopers.interfaces.api.user

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1", description = "사용자 관리 API")
interface UserV1ApiSpec {
    @Operation(summary = "회원 가입", description = "새로운 사용자를 등록합니다.")
    fun registerUser(request: UserV1Dto.RegisterUserRequest): ApiResponse<UserV1Dto.UserResponse>

    @Operation(summary = "내 정보 조회", description = "사용자 ID로 정보를 조회합니다.")
    fun getUserByUserId(userId: String): ApiResponse<UserV1Dto.UserResponse>

    @Operation(summary = "포인트 조회", description = "사용자의 포인트를 조회합니다.")
    fun getPoint(userId: String?): ApiResponse<UserV1Dto.PointResponse>

    @Operation(summary = "포인트 충전", description = "사용자의 포인트를 충전합니다.")
    fun chargePoint(userId: String?, request: UserV1Dto.ChargePointRequest): ApiResponse<UserV1Dto.PointResponse>
}
