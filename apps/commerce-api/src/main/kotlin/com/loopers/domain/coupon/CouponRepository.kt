package com.loopers.domain.coupon

interface CouponRepository {
    fun save(coupon: Coupon): Coupon
    fun findById(id: Long): Coupon?
}
