package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import org.springframework.stereotype.Repository

@Repository
class CouponRepositoryImpl(private val couponJpaRepository: CouponJpaRepository) : CouponRepository {
    override fun findById(id: Long): Coupon? =
        couponJpaRepository.findById(id).orElse(null)

    override fun save(coupon: Coupon): Coupon =
        couponJpaRepository.save(coupon)
}
