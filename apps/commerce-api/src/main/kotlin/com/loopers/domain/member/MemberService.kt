package com.loopers.domain.member

import com.loopers.domain.shared.Email
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberService(
    private val memberRepository: MemberRepository,
) {
    @Transactional
    fun joinMember(memberId: MemberId, email: Email, birthDate: BirthDate, gender: Gender): Member {
        // 중복 체크
        memberRepository.findByMemberId(memberId)?.let {
            throw DuplicateMemberIdException(ErrorType.BAD_REQUEST, "이미 가입된 유저 ID입니다.")
        }

        val member = Member(memberId, email, birthDate, gender)
        return memberRepository.save(member)
    }

    @Transactional(readOnly = true)
    fun getMemberByMemberId(memberId: MemberId): Member? {
        // 찾지 못한 경우 null 반환
        return memberRepository.findByMemberId(memberId)
    }

    // 포인트 충전
    @Transactional
    fun chargePoint(memberId: MemberId, amount: Long): Long {
        val member = memberRepository.findByMemberId(memberId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "유저를 찾을 수 없습니다.")

        member.chargePoint(amount)
        memberRepository.save(member) // 명시적 저장
        return member.point.amount
    }
}
