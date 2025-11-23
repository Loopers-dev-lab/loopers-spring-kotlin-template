package com.loopers.domain.coupon

interface CouponIssueRepository {
    fun findBy(userId: Long, couponId: Long): CouponIssue?
}
