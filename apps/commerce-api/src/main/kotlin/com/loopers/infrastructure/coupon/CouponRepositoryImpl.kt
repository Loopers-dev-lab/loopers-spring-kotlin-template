package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(private val couponJpaRepository: CouponJpaRepository) : CouponRepository {
    override fun getNotUsedByCouponIdWithPessimisticLock(couponId: Long): CouponModel {
        return couponJpaRepository.findNotUsedByCouponIdWithPessimisticLock(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용 가능한 쿠폰이 존재하지 않습니다.")
    }

    override fun save(couponModel: CouponModel): CouponModel {
        return couponJpaRepository.save(couponModel)
    }
}
