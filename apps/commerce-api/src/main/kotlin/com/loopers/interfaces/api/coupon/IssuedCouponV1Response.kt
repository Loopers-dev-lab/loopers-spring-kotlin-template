package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.IssuedCouponV1Info
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UsageStatus
import org.springframework.data.domain.Slice

class IssuedCouponV1Response {

    data class Issue(
        val issuedCouponId: Long,
        val userId: Long,
        val couponId: Long,
        val status: UsageStatus,
    ) {
        companion object {
            fun from(info: IssuedCouponV1Info.Issue): Issue {
                return Issue(
                    issuedCouponId = info.issuedCouponId,
                    userId = info.userId,
                    couponId = info.couponId,
                    status = info.status,
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
            fun from(info: IssuedCouponV1Info.UserCoupon): UserCoupon {
                return UserCoupon(
                    issuedCouponId = info.issuedCouponId,
                    couponId = info.couponId,
                    name = info.name,
                    discountType = info.discountType,
                    discountValue = info.discountValue,
                    status = info.status,
                )
            }
        }
    }

    data class UserCoupons(
        val coupons: List<UserCoupon>,
        val hasNext: Boolean,
    ) {
        companion object {
            fun from(slice: Slice<IssuedCouponV1Info.UserCoupon>): UserCoupons {
                return UserCoupons(
                    coupons = slice.content.map { UserCoupon.from(it) },
                    hasNext = slice.hasNext(),
                )
            }
        }
    }
}
