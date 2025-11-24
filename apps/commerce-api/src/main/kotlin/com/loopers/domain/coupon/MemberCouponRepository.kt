package com.loopers.domain.coupon

interface MemberCouponRepository {
    fun findByMemberIdAndCouponId(memberId: Long, couponId: Long): MemberCoupon?
    fun findByMemberIdAndCouponIdWithLock(memberId: String, couponId: Long): MemberCoupon?
    fun save(memberCoupon: MemberCoupon): MemberCoupon
}
