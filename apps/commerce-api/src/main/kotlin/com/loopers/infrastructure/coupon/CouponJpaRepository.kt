package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponModel
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CouponJpaRepository : JpaRepository<CouponModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponModel c WHERE c.id = :couponId AND c.refUserId = :userId AND c.isUsed = false")
    fun findNotUsedByCouponIdWithPessimisticLock(couponId: Long, userId: Long): CouponModel?

    fun findByIdAndRefUserIdAndIsUsed(couponId: Long, userId: Long, isUsed: Boolean): CouponModel?
}
