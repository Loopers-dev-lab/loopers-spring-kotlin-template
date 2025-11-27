package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.ZonedDateTime

@Entity
@Table(
    name = "issued_coupons",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_user_coupon",
            columnNames = ["user_id", "coupon_id"],
        ),
    ],
    indexes = [
        Index(
            name = "idx_issued_coupon_user_id",
            columnList = "user_id",
        ),
    ],
)
class IssuedCoupon(
    userId: Long,
    couponId: Long,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        private set

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long = couponId
        private set

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: UsageStatus = UsageStatus.AVAILABLE
        private set

    @Column(name = "used_at")
    var usedAt: ZonedDateTime? = null
        private set

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        private set

    /**
     * 쿠폰을 사용하고 할인 금액을 반환합니다.
     *
     * @param userId 쿠폰을 사용하려는 사용자 ID
     * @param coupon 사용할 쿠폰 정의
     * @param orderAmount 주문 금액
     * @param usedAt 쿠폰 사용 시각
     * @return 할인 금액
     * @throws CoreException 사용자가 일치하지 않거나, 쿠폰 정보가 일치하지 않거나, 이미 사용된 쿠폰인 경우
     */
    fun use(userId: Long, coupon: Coupon, orderAmount: Money, usedAt: ZonedDateTime): Money {
        if (this.userId != userId) {
            throw CoreException(ErrorType.BAD_REQUEST, "보유하지 않은 쿠폰입니다")
        }

        if (coupon.id != couponId) {
            throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 정보가 일치하지 않습니다")
        }

        if (status == UsageStatus.USED) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다")
        }

        status = UsageStatus.USED
        this.usedAt = usedAt

        return coupon.calculateDiscount(orderAmount)
    }

    companion object {
        // TODO(toong): IssuedCoupon.issue 보다는 coupon.issue가 더 명시적인 시그니처로 보임.
        fun issue(userId: Long, coupon: Coupon): IssuedCoupon {
            return IssuedCoupon(userId, coupon.id)
        }
    }
}
