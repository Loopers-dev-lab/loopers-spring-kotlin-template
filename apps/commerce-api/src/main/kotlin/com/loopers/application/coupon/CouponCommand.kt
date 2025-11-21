package com.loopers.application.coupon

/**
 * 쿠폰 발급 Command
 */
data class IssueCouponCommand(
    val memberId: String,
    val couponId: Long,
)

/**
 * 쿠폰 사용 Command
 */
data class UseCouponCommand(
    val memberCouponId: String,
)
