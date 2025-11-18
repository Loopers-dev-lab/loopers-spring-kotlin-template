package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.ZonedDateTime

@Entity
@Table(
    name = "coupon_issue",
    indexes = [
        Index(name = "idx_coupon_issue_user_id", columnList = "user_id"),
        Index(name = "idx_coupon_issue_coupon_id", columnList = "coupon_id"),
    ],
)
class CouponIssue(
    val couponId: Long,

    val userId: Long,

    @Enumerated(EnumType.STRING)
    var status: CouponStatus = CouponStatus.ISSUED,

    var usedAt: ZonedDateTime? = null,

    val issuedAt: ZonedDateTime = ZonedDateTime.now(),

    @Version
    var version: Long = 0

) : BaseEntity() {
    companion object {
        fun issue(couponId: Long, userId: Long): CouponIssue {
            return CouponIssue(
                couponId = couponId,
                userId = userId,
            )
        }
    }

    fun use() {
        require(isUsable()) { "이미 사용된 쿠폰입니다" }
        this.status = CouponStatus.USED
        this.usedAt = ZonedDateTime.now()
    }

    private fun isUsable(): Boolean {
        return status == CouponStatus.ISSUED
    }
}
