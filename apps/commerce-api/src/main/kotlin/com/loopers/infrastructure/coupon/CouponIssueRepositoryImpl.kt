package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssue
import com.loopers.domain.coupon.CouponIssueRepository
import org.springframework.stereotype.Repository

@Repository
class CouponIssueRepositoryImpl(
    private val couponIssueJpaRepository: CouponIssueJpaRepository,
) : CouponIssueRepository {
    override fun findBy(userId: Long, couponId: Long): CouponIssue? {
        return couponIssueJpaRepository.findByUserIdAndCouponIdWithLock(userId, couponId)
    }
}
