package com.loopers.infrastructure.member

import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<Member, Long> {
    fun findByMemberId(memberId: MemberId): Member?
}
