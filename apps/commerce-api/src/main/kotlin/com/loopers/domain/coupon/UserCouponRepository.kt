package com.loopers.domain.coupon

interface UserCouponRepository {
    fun findById(id: Long): UserCoupon?
    fun findByIdAndUserIdWithLock(id: Long, userId: Long): UserCoupon?
    fun findByUserIdAndCouponId(userId: Long, couponId: Long): UserCoupon?
    fun save(userCoupon: UserCoupon): UserCoupon
}
