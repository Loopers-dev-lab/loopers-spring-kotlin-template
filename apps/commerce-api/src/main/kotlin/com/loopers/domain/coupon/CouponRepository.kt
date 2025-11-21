package com.loopers.domain.coupon

interface CouponRepository {

    fun getNotUsedByCouponIdWithPessimisticLock(couponId: Long): CouponModel

    fun save(couponModel: CouponModel): CouponModel
}
