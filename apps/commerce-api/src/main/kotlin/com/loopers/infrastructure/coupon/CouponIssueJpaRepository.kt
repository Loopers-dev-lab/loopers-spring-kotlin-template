package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssue
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints

interface CouponIssueJpaRepository : JpaRepository<CouponIssue, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT ci FROM CouponIssue ci WHERE ci.userId = :userId AND ci.couponId = :couponId")
    fun findByUserIdAndCouponIdWithLock(userId: Long, couponId: Long): CouponIssue?
}
