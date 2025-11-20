package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponService
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Component

@Component
class CouponFacade(
    private val couponService: CouponService,
) {
    fun issueCoupon(userId: Long, couponId: Long): IssuedCouponV1Info.Issue {
        return couponService.issueCoupon(userId, couponId)
            .let { IssuedCouponV1Info.Issue.from(it) }
    }

    fun findUserCoupons(criteria: IssuedCouponCriteria.FindUserCoupons): Slice<IssuedCouponV1Info.UserCoupon> {
        val command = criteria.to()
        val slicedViews = couponService.findUserCoupons(command)
        return slicedViews.map { IssuedCouponV1Info.UserCoupon.from(it) }
    }
}
