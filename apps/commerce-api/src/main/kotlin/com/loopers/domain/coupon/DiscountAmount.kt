package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Embeddable
data class DiscountAmount(
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    val type: DiscountType,

    @Column(name = "discount_value", nullable = false)
    val value: Long,
) {
    init {
        if (value <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0보다 커야 합니다")
        }

        if (type == DiscountType.RATE && value > 100) {
            throw CoreException(ErrorType.BAD_REQUEST, "할인율은 100 이하여야 합니다")
        }
    }

    /**
     * 주문 금액에 대한 할인 금액을 계산합니다.
     *
     * @param orderAmount 주문 금액
     * @return 계산된 할인 금액 (주문 금액을 초과하지 않음)
     */
    fun calculate(orderAmount: Money): Money {
        val discountedAmount = when (type) {
            DiscountType.FIXED_AMOUNT -> Money.krw(value)
            DiscountType.RATE -> orderAmount.applyPercentage(value)
        }
        return discountedAmount.min(orderAmount).round(0)
    }
}
