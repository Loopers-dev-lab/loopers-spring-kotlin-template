package com.loopers.domain.coupon

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

enum class CouponType(
    val description: String
) {
    FIXED_AMOUNT("정액 쿠폰") {
        override fun validate(discountAmount: Long?, discountRate: Int?) {
            if (discountAmount == null || discountAmount <= 0) {
                throw CoreException(
                    ErrorType.INVALID_COUPON_DISCOUNT,
                    "정액 쿠폰은 할인 금액이 필수입니다."
                )
            }
            if (discountRate != null) {
                throw CoreException(
                    ErrorType.INVALID_COUPON_DISCOUNT,
                    "정액 쿠폰은 할인율을 가질 수 없습니다."
                )
            }
        }

        override fun calculateDiscount(
            discountAmount: Long?,
            discountRate: Int?,
            orderAmount: Money,
        ): Money {
            val discount = Money.of(discountAmount!!)
            return if (orderAmount.isGreaterThanOrEqual(discount)) {
                discount
            } else {
                orderAmount // 주문 금액보다 큰 경우 주문 금액만큼만 할인
            }
        }
    },
    PERCENTAGE("정률 쿠폰") {
        override fun validate(discountAmount: Long?, discountRate: Int?) {
            if (discountRate == null || discountRate !in 1..100) {
                throw CoreException(
                    ErrorType.INVALID_COUPON_DISCOUNT,
                    "정률 쿠폰은 1~100 사이의 할인율이 필수입니다."
                )
            }
            if (discountAmount != null) {
                throw CoreException(
                    ErrorType.INVALID_COUPON_DISCOUNT,
                    "정률 쿠폰은 할인 금액을 가질 수 없습니다."
                )
            }
        }

        override fun calculateDiscount(
            discountAmount: Long?,
            discountRate: Int?,
            orderAmount: Money,
        ): Money {
            val rate = discountRate!!
            val discountValue = (orderAmount.amount * rate) / 100
            return Money.of(discountValue)
        }

    };


    abstract fun validate(
        discountAmount: Long?,
        discountRate: Int?
    )

    abstract fun calculateDiscount(
        discountAmount: Long?,
        discountRate: Int?,
        orderAmount: Money
    ): Money

}
