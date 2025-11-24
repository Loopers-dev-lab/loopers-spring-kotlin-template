package com.loopers.domain.coupon

interface CouponRepository {
    fun findById(id: Long): Coupon?
    fun findByIdOrThrow(id: Long): Coupon
}
