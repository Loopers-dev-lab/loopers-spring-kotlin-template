package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
}
