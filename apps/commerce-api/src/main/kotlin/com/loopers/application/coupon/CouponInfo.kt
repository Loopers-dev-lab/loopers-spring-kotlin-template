package com.loopers.application.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.MemberCoupon

/**
 * 쿠폰 정보
 */
data class CouponInfo(
    val id: Long,
    val name: String,
    val description: String?,
    val couponType: CouponType,
    val discountAmount: Long?,
    val discountRate: Int?,
) {

    companion object {
        fun from(coupon: Coupon): CouponInfo {
            return CouponInfo(
                id = coupon.id,
                name = coupon.name,
                description = coupon.description,
                couponType = coupon.couponType,
                discountAmount = coupon.discountAmount,
                discountRate = coupon.discountRate,
            )
        }

        fun fromList(coupons: List<Coupon>): List<CouponInfo> {
            return coupons.map { from(it) }
        }
    }
}


/**
 * 회원 쿠폰 정보
 */
data class MemberCouponInfo(
    val id: Long,
    val memberId: String,
    val coupon: CouponInfo,
    val isUsed: Boolean,
    val usedAt: String?,
    val createdAt: String,
) {
    companion object {
        fun from(memberCoupon: MemberCoupon): MemberCouponInfo {
            return MemberCouponInfo(
                id = memberCoupon.id,
                memberId = memberCoupon.memberId,
                coupon = CouponInfo.from(memberCoupon.coupon),
                isUsed = memberCoupon.usedAt != null,
                usedAt = memberCoupon.usedAt?.toString(),
                createdAt = memberCoupon.createdAt.toString(),
            )
        }
    }
}

/**
 * 할인 계산 결과
 */
data class DiscountInfo(
    val orderAmount: Long,
    val discountAmount: Long,
    val finalAmount: Int,
)
