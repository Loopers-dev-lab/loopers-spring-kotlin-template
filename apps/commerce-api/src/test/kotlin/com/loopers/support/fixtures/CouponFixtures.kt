package com.loopers.support.fixtures

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponIssue
import com.loopers.domain.coupon.DiscountType

object CouponFixtures {
    fun createCoupon(
        name: String = "테스트 쿠폰",
        discountType: DiscountType = DiscountType.FIXED,
        discountValue: Long = 5000L,
    ): Coupon {
        return Coupon.create(
            name = name,
            discountType = discountType,
            discountValue = discountValue,
        )
    }

    fun createCoupon(
        id: Long = 1L,
        name: String = "테스트 쿠폰",
        discountType: DiscountType = DiscountType.FIXED,
        discountValue: Long = 5000L,
    ): Coupon {
        return Coupon.create(
            name = name,
            discountType = discountType,
            discountValue = discountValue,
        ).withId(id)
    }

    fun createCouponIssue(
        couponId: Long = 1L,
        userId: Long = 1L,
    ): CouponIssue {
        return CouponIssue.issue(
            couponId = couponId,
            userId = userId,
        )
    }

    fun createCouponIssue(
        id: Long = 1L,
        couponId: Long = 1L,
        userId: Long = 1L,
    ): CouponIssue {
        return CouponIssue.issue(
            couponId = couponId,
            userId = userId,
        ).withId(id)
    }
}
