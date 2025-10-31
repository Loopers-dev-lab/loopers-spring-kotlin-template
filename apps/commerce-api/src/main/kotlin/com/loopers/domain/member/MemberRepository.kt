package com.loopers.domain.member

interface MemberRepository {
    fun find(id: Long): Member?

    fun findByMemberId(memberId: MemberId): Member?

    fun save(member: Member): Member
}
