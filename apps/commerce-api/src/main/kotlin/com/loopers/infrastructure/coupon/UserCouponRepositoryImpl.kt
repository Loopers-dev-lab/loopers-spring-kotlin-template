package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import org.springframework.stereotype.Repository

@Repository
class UserCouponRepositoryImpl(private val userCouponJpaRepository: UserCouponJpaRepository) : UserCouponRepository {
    override fun findById(id: Long): UserCoupon? =
        userCouponJpaRepository.findById(id).orElse(null)

    override fun findByIdAndUserIdWithLock(id: Long, userId: Long): UserCoupon? =
        userCouponJpaRepository.findByIdAndUserIdWithLock(id, userId)

    override fun findByUserIdAndCouponId(userId: Long, couponId: Long): UserCoupon? =
        userCouponJpaRepository.findByUserIdAndCouponId(userId, couponId)

    override fun save(userCoupon: UserCoupon): UserCoupon =
        userCouponJpaRepository.save(userCoupon)
}
