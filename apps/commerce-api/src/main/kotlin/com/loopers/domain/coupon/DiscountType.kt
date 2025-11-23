package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.math.BigDecimal
import java.math.RoundingMode

enum class DiscountType(val description: String) {
    PERCENTAGE(
        description = "주문 금액의 일정 비율만큼 할인",
    ) {
        override fun calculateDiscountPrice(orderAmount: BigDecimal, discountValue: BigDecimal): BigDecimal {
            if (discountValue < BigDecimal.ZERO || discountValue >= BigDecimal(100)) {
                throw CoreException(ErrorType.BAD_REQUEST, "할인율은 0과 100 사이여야 합니다.")
            }

            return orderAmount
                .multiply(discountValue)
                .divide(BigDecimal(100), 0, RoundingMode.DOWN)
        }
    },

    FIXED_AMOUNT(
        description = "고정된 금액 만큼 할인",
    ) {
        override fun calculateDiscountPrice(orderAmount: BigDecimal, discountValue: BigDecimal): BigDecimal {
            if (discountValue < BigDecimal.ZERO) {
                throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 이상이어야 합니다.")
            }

            if (discountValue > orderAmount) {
                throw CoreException(ErrorType.BAD_REQUEST, "할인 금액이 주문보다 클 수 없습니다.")
            }

            return discountValue
        }
    },
    ;

    abstract fun calculateDiscountPrice(orderAmount: BigDecimal, discountValue: BigDecimal): BigDecimal
}
