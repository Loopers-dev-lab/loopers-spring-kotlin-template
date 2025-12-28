package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "user_coupons")
class UserCoupon(
    userId: Long,
    coupon: Coupon,
) : BaseEntity() {
    @Column(nullable = false)
    var userId: Long = userId
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    var coupon: Coupon = coupon
        protected set

    @Column(nullable = false)
    var isUsed: Boolean = false
        protected set

    @Column(nullable = true)
    var usedAt: LocalDateTime? = null
        protected set

    /**
     * 쿠폰 사용 가능 여부 확인
     */
    fun canUse(): Boolean = !isUsed

    /**
     * 쿠폰 사용 처리
     */
    fun use() {
        if (isUsed) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "이미 사용된 쿠폰입니다",
            )
        }

        isUsed = true
        usedAt = LocalDateTime.now()
    }
}
