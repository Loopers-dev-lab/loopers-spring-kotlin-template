package com.loopers.interfaces.api.member

import com.loopers.application.member.MemberFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class MemberV1Controller(
    private val memberFacade: MemberFacade,
) : MemberV1ApiSpec {

    @PostMapping
    override fun join(
        @Valid @RequestBody request: JoinMemberRequest
    ): ApiResponse<MemberV1Dto.MemberResponse> {

        return memberFacade.joinMember(request.toCommand())
            .let { MemberV1Dto.MemberResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/me")
    override fun getMe(
        @RequestHeader("X-USER-ID") memberId: String
    ): ApiResponse<MemberV1Dto.MemberResponse> {
        return memberFacade.getMemberByMemberId(memberId)
            ?.let { MemberV1Dto.MemberResponse.from(it) }
            ?.let { ApiResponse.success(it) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다")
    }

}
