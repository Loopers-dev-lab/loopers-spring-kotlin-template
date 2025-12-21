package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "coupons")
class Coupon(
    name: String,
    description: String?,
    couponType: CouponType,
    discountAmount: Long?, // 정액 쿠폰용
    discountRate: Int?,    // 정률 쿠폰용
) : BaseEntity() {

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = description
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var couponType: CouponType = couponType
        protected set

    @Column(name = "discount_amount")
    var discountAmount: Long? = discountAmount
        protected set

    @Column(name = "discount_rate")
    var discountRate: Int? = discountRate
        protected set

    init {
        validateCouponFields()
    }

    private fun validateCouponFields() {
        couponType.validate(discountAmount, discountRate)
    }

    fun calculateDiscount(orderAmount: Money): Money {
        return couponType.calculateDiscount(discountAmount, discountRate, orderAmount)
    }
}
