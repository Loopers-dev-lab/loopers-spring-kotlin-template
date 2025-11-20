package com.loopers.domain.coupon

import org.springframework.data.domain.Slice

interface IssuedCouponRepository {
    fun findByUserIdAndCouponId(userId: Long, couponId: Long): IssuedCoupon?
    fun findAllByUserId(userId: Long): List<IssuedCoupon>
    fun findAllBy(query: IssuedCouponPageQuery): Slice<IssuedCoupon>
    fun findById(id: Long): IssuedCoupon?
    fun save(issuedCoupon: IssuedCoupon): IssuedCoupon
}
