package com.loopers.application.member

import com.loopers.domain.member.BirthDate
import com.loopers.domain.member.Gender
import com.loopers.domain.member.MemberId
import com.loopers.domain.member.MemberService
import com.loopers.domain.shared.Email
import org.springframework.stereotype.Component

@Component
class MemberFacade(
    private val memberService: MemberService,
) {
    // 회원 가입
    fun joinMember(command: JoinMemberCommand): MemberInfo {
        return memberService.joinMember(
            memberId = MemberId(command.memberId),
            email = Email(command.email),
            birthDate = BirthDate.from(command.birthDate),
            gender = Gender.valueOf(command.gender.uppercase())
        ).let { MemberInfo.from(it) }
    }

    // 회원 정보 조회
    fun getMemberByMemberId(memberId: String): MemberInfo? {
        return memberService.getMemberByMemberId(MemberId(memberId))
            ?.let { MemberInfo.from(it) }
    }

    // 포인트 조회
    fun getPoint(memberId: String): Long? {
        return memberService.getMemberByMemberId(MemberId(memberId))?.point?.amount
    }

    // 포인트 충전
    fun chargePoint(memberId: String, amount: Long): Long {
        return memberService.chargePoint(MemberId(memberId), amount)
    }

}
