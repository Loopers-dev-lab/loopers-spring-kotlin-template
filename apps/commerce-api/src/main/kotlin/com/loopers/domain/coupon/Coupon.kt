package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.math.RoundingMode

@Entity
@Table(name = "coupons")
class Coupon(name: String, discountType: CouponType, discountValue: BigDecimal) : BaseEntity() {
    @Column(nullable = false, length = 100)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var discountType: CouponType = discountType
        protected set

    @Column(nullable = false, precision = 15, scale = 2)
    var discountValue: BigDecimal = discountValue
        protected set

    init {
        if (discountValue <= BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.")
        }

        if (discountType == CouponType.PERCENTAGE &&
            discountValue > BigDecimal("100")
        ) {
            throw CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰의 할인율은 100을 초과할 수 없습니다.")
        }
    }

    /**
     * 주문 금액에 대한 할인 금액을 계산한다
     */
    fun calculateDiscount(orderAmount: Money): Money {
        val discountAmount = when (discountType) {
            CouponType.FIXED_AMOUNT -> discountValue

            CouponType.PERCENTAGE ->
                orderAmount.amount
                    .multiply(discountValue)
                    .divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
        }

        // 할인 금액이 주문 금액보다 클 수 없음
        val finalDiscountAmount = if (discountAmount > orderAmount.amount) {
            orderAmount.amount
        } else {
            discountAmount
        }

        return Money(finalDiscountAmount, orderAmount.currency)
    }
}
