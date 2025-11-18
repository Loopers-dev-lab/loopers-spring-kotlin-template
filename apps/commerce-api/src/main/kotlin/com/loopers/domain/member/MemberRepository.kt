package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

interface MemberRepository {
    fun find(id: Long): Member?

    fun findByMemberId(memberId: MemberId): Member?

    fun save(member: Member): Member

    fun findByIdOrThrow(id: Long): Member {
        return find(id)
            ?: throw CoreException(ErrorType.MEMBER_NOT_FOUND, "회원을 찾을 수 없습니다. id: $id")
    }

    fun findByMemberIdOrThrow(memberId: String): Member {
        return findByMemberId(MemberId(memberId))
            ?: throw CoreException(ErrorType.MEMBER_NOT_FOUND, "회원을 찾을 수 없습니다. memberId: $memberId")
    }
}
