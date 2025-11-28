package com.loopers.domain.coupon

interface UserCouponRepository {
    fun save(userCoupon: UserCoupon): UserCoupon
    fun findById(id: Long): UserCoupon?
    fun findByIdWithLock(id: Long): UserCoupon?
}
