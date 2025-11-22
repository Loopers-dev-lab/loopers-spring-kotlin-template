package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import org.springframework.data.jpa.repository.JpaRepository

interface CouponJpaRepository : JpaRepository<Coupon, Long>
