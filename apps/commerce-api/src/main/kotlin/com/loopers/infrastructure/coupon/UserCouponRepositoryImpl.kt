package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class UserCouponRepositoryImpl(
    private val jpa: UserCouponJpaRepository,
) : UserCouponRepository {

    override fun save(userCoupon: UserCoupon): UserCoupon {
        return jpa.save(userCoupon)
    }

    override fun findById(id: Long): UserCoupon? {
        return jpa.findByIdOrNull(id)
    }

    override fun findByIdWithLock(id: Long): UserCoupon? {
        return jpa.findByIdWithLock(id)
    }
}
