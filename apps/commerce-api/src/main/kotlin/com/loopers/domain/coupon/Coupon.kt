package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
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
@Table(name = "loopers_coupon")
class Coupon(
    @Column(nullable = false, length = 100)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: CouponType,

    @Column(nullable = false, precision = 10, scale = 2)
    val discountValue: BigDecimal,
) : BaseEntity() {

    init {
        validateDiscountValue()
    }

    companion object {
        fun of(name: String, type: CouponType, discountValue: BigDecimal): Coupon {
            return Coupon(
                name = name,
                type = type,
                discountValue = discountValue,
            )
        }
    }

    fun calculateDiscount(originalAmount: BigDecimal): BigDecimal {
        return when (type) {
            CouponType.FIXED_AMOUNT -> {
                if (discountValue > originalAmount) originalAmount else discountValue
            }
            CouponType.PERCENTAGE -> {
                originalAmount.multiply(discountValue)
                    .divide(BigDecimal(100), 2, RoundingMode.DOWN)
            }
        }
    }

    private fun validateDiscountValue() {
        if (discountValue <= BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "할인 금액/비율은 0보다 커야 합니다.")
        }

        if (type == CouponType.PERCENTAGE && discountValue > BigDecimal(100)) {
            throw CoreException(ErrorType.BAD_REQUEST, "할인 비율은 100%를 초과할 수 없습니다.")
        }
    }
}
