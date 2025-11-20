package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class CouponRdbRepository(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {

    @Transactional(readOnly = true)
    override fun findById(id: Long): Coupon? {
        return couponJpaRepository.findByIdOrNull(id)
    }

    @Transactional(readOnly = true)
    override fun findAllByIds(ids: List<Long>): List<Coupon> {
        if (ids.isEmpty()) return emptyList()
        return couponJpaRepository.findAllById(ids)
    }

    @Transactional
    override fun save(coupon: Coupon): Coupon {
        return couponJpaRepository.save(coupon)
    }
}
