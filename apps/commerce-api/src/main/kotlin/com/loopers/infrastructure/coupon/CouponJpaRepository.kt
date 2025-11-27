package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CouponJpaRepository : JpaRepository<Coupon, Long>
