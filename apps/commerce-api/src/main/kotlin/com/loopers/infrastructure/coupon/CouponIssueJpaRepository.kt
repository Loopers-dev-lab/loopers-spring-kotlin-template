package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssue
import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueJpaRepository : JpaRepository<CouponIssue, Long> {
    fun findByUserIdAndCouponId(userId: Long, couponId: Long): CouponIssue?
}
