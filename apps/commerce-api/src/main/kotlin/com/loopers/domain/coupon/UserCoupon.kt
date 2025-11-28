package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "loopers_user_coupon")
class UserCoupon(
    @Column(nullable = false)
    val userId: Long,

    @Column(nullable = false)
    val couponId: Long,

    @Column(nullable = false)
    var used: Boolean = false,
) : BaseEntity() {

    companion object {
        fun of(userId: Long, couponId: Long): UserCoupon {
            return UserCoupon(
                userId = userId,
                couponId = couponId,
                used = false,
            )
        }
    }

    fun use() {
        if (used) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.")
        }
        used = true
    }

    fun isAvailable(): Boolean {
        return !used
    }
}
