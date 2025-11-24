package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun findById(id: Long): Coupon? {
        return couponJpaRepository.findById(id).orElse(null)
    }

    override fun findByIdOrThrow(id: Long): Coupon {
        return findById(id)
            ?: throw CoreException(
                ErrorType.COUPON_NOT_FOUND,
                "쿠폰을 찾을 수 없습니다. ID: $id"
            )
    }
}
