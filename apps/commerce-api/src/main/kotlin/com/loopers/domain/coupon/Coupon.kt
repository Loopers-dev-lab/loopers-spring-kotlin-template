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
     * @return 계산된 할인 금액
     */
    fun calculateDiscount(orderAmount: Money): Money {
        return discountAmount.calculate(orderAmount)
    }

    companion object {
        fun of(name: String, discountAmount: DiscountAmount): Coupon {
            return Coupon(name, discountAmount)
        }
    }
}
