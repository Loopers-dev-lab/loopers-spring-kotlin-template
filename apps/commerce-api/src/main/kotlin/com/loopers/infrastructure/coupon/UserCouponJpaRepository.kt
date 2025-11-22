package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCouponJpaRepository : JpaRepository<UserCoupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCoupon uc WHERE uc.id = :id AND uc.userId = :userId AND uc.deletedAt IS NULL")
    fun findByIdAndUserIdWithLock(
        @Param("id") id: Long,
        @Param("userId") userId: Long,
    ): UserCoupon?

    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.coupon.id = :couponId AND uc.deletedAt IS NULL")
    fun findByUserIdAndCouponId(
        @Param("userId") userId: Long,
        @Param("couponId") couponId: Long,
    ): UserCoupon?
}
