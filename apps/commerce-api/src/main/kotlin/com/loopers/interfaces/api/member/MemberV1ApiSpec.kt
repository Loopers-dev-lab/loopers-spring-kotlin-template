package com.loopers.interfaces.api.member

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Member V1 API", description = "회원 관련 API 입니다.")
interface MemberV1ApiSpec {
    @Operation(
        summary = "회원 가입",
        description = "회원 가입합니다.",
    )
    fun join(
        @Schema(name = "회원 정보", description = "가입할 회원 정보")
        request: JoinMemberRequest,
    ): ApiResponse<MemberV1Dto.MemberResponse>

    @Operation(
        summary = "회원 정보 조회",
        description = "ID로 회원 정보를 조회합니다.",
    )
    fun getMemberByMemberId(
        @Schema(name = "회원 ID", description = "조회할 회원의 ID")
        memberId: String,
    ): ApiResponse<MemberV1Dto.MemberResponse>

}
