package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.values.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "coupons")
class Coupon(
    name: String,
    discountAmount: DiscountAmount,
) : BaseEntity() {

    @Column(name = "name", nullable = false)
    var name: String = name
        private set

    @Embedded
    @AttributeOverride(name = "type", column = Column(name = "discount_type"))
    @AttributeOverride(name = "value", column = Column(name = "discount_value"))
    var discountAmount: DiscountAmount = discountAmount
        private set

    /**
     * 주문 금액에 대한 할인 금액을 계산합니다.
     *
     * @param orderAmount 주문 금액
     * @param policy 적용할 할인 정책
     * @return 계산된 할인 금액
     */
    fun calculateDiscount(orderAmount: Money, policy: DiscountPolicy): Money {
        return policy.calculate(orderAmount, this)
    }

    /**
     * 쿠폰을 사용자에게 발급합니다.
     *
     * @param userId 발급 대상 사용자 ID
     * @return 발급된 쿠폰
     */
    fun issue(userId: Long): IssuedCoupon {
        return IssuedCoupon(userId, this.id)
    }

    companion object {
        fun of(name: String, discountAmount: DiscountAmount): Coupon {
            return Coupon(name, discountAmount)
        }
    }
}
