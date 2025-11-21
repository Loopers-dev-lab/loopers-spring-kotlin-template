package com.loopers.infrastructure.member

import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface MemberJpaRepository : JpaRepository<Member, Long> {
    fun findByMemberId(memberId: MemberId): Member?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.memberId.value = :memberId AND m.deletedAt IS NULL")
    fun findByMemberIdWithLock(memberId: String): Member?
}
