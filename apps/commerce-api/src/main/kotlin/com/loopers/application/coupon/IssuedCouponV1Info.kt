package com.loopers.application.coupon

import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponView
import com.loopers.domain.coupon.UsageStatus

class IssuedCouponV1Info {
    data class Issue(
        val issuedCouponId: Long,
        val userId: Long,
        val couponId: Long,
        val status: UsageStatus,
    ) {
        companion object {
            fun from(issuedCoupon: IssuedCoupon): Issue {
                return Issue(
                    issuedCouponId = issuedCoupon.id,
                    userId = issuedCoupon.userId,
                    couponId = issuedCoupon.couponId,
                    status = issuedCoupon.status,
                )
            }
        }
    }

    data class UserCoupon(
        val issuedCouponId: Long,
        val couponId: Long,
        val name: String,
        val discountType: DiscountType,
        val discountValue: Long,
        val status: UsageStatus,
    ) {
        companion object {
            fun from(view: IssuedCouponView): UserCoupon {
                return UserCoupon(
                    issuedCouponId = view.issuedCoupon.id,
                    couponId = view.coupon.id,
                    name = view.coupon.name,
                    discountType = view.coupon.discountAmount.type,
                    discountValue = view.coupon.discountAmount.value,
                    status = view.issuedCoupon.status,
                )
            }
        }
    }

    data class UserCoupons(
        val coupons: List<UserCoupon>,
    ) {
        companion object {
            fun from(views: List<IssuedCouponView>): UserCoupons {
                return UserCoupons(views.map { UserCoupon.from(it) })
            }
        }
    }
}
