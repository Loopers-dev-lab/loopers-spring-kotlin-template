package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.IssuedCoupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IssuedCouponJpaRepository : JpaRepository<IssuedCoupon, Long> {
    fun findByUserIdAndCouponId(userId: Long, couponId: Long): IssuedCoupon?
    fun findAllByUserId(userId: Long): List<IssuedCoupon>
}
