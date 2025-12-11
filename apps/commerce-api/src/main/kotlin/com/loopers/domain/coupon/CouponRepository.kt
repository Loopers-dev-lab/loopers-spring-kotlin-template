package com.loopers.domain.coupon

interface CouponRepository {

    fun getNotUsedByCouponIdWithPessimisticLock(couponId: Long, userId: Long): CouponModel

    fun findByCouponIdAndUserId(couponId: Long, userId: Long): CouponModel?

    fun save(couponModel: CouponModel): CouponModel
}
