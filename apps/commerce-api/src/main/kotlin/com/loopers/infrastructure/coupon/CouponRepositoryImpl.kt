package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(private val couponJpaRepository: CouponJpaRepository) : CouponRepository {
    override fun getNotUsedByCouponIdWithPessimisticLock(
        couponId: Long,
        userId: Long,
    ): CouponModel = couponJpaRepository.findNotUsedByCouponIdWithPessimisticLock(couponId, userId)
        ?: throw CoreException(ErrorType.NOT_FOUND, "사용 가능한 쿠폰이 존재하지 않습니다.")

    override fun save(couponModel: CouponModel): CouponModel = couponJpaRepository.save(couponModel)
}
