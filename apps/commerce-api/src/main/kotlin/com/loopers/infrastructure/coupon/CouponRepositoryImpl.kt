package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class CouponRepositoryImpl(
    private val jpa: CouponJpaRepository,
) : CouponRepository {

    override fun save(coupon: Coupon): Coupon {
        return jpa.save(coupon)
    }

    override fun findById(id: Long): Coupon? {
        return jpa.findByIdOrNull(id)
    }
}
