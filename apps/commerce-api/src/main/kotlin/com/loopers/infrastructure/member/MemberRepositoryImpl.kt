package com.loopers.infrastructure.member

import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import com.loopers.domain.member.MemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class MemberRepositoryImpl(
    private val memberJpaRepository: MemberJpaRepository,
) : MemberRepository {
    override fun find(id: Long): Member? {
        return memberJpaRepository.findByIdOrNull(id)
    }

    override fun findByMemberId(memberId: MemberId): Member? {
        return memberJpaRepository.findByMemberId(memberId)
    }

    override fun save(member: Member): Member {
        return memberJpaRepository.save(member)
    }
}
