package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "member_coupons")
class MemberCoupon(
    memberID: String,
    coupon: Coupon,
) : BaseEntity() {

    @Column(name = "member_id", nullable = false, length = 50)
    var memberId: String = memberID
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    var coupon: Coupon = coupon
        protected set

    @Column(name = "used_at")
    var usedAt: ZonedDateTime? = null
        protected set

    fun canUse(): Boolean {
        return usedAt == null
    }

    fun use() {
        if (!canUse()) {
            throw CoreException(
                ErrorType.COUPON_ALREADY_USED,
                "이미 사용된 쿠폰입니다. 쿠폰 ID: ${coupon.id}"
            )
        }
        this.usedAt = ZonedDateTime.now()
    }

    fun calculateDiscount(orderAmount: Money): Money {
        return coupon.calculateDiscount(orderAmount)
    }

    companion object {
        fun issue(memberID: String, coupon: Coupon): MemberCoupon {
            return MemberCoupon(memberID, coupon)
        }
    }
}
