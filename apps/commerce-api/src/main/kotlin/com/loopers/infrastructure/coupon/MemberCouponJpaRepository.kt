package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.MemberCoupon
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface MemberCouponJpaRepository : JpaRepository<MemberCoupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT mc FROM MemberCoupon mc
        JOIN FETCH mc.coupon c
        WHERE mc.memberId = :memberId
        AND mc.coupon.id = :couponId
        AND mc.deletedAt IS NULL
        AND c.deletedAt IS NULL
    """)
    fun findByMemberIdAndCouponIdWithLock(memberId: String, couponId: Long): MemberCoupon?
}
