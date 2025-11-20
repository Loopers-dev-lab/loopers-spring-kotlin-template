package com.loopers.domain.coupon

interface CouponRepository {
    fun findById(id: Long): Coupon?
    fun findAllByIds(ids: List<Long>): List<Coupon>
    fun save(coupon: Coupon): Coupon
}
