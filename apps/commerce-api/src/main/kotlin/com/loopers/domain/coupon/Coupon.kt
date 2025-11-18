package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "coupon")
class Coupon(
    val name: String,

    @Enumerated(EnumType.STRING)
    val discountType: DiscountType,

    val discountValue: Long,

) : BaseEntity() {
    companion object {
        fun create(name: String, discountType: DiscountType, discountValue: Long): Coupon {
            require(name.isNotBlank()) { "쿠폰명은 필수입니다" }
            if (discountType == DiscountType.RATE) {
                require(discountValue in 1..100) { "할인율은 1~100 사이여야 합니다" }
            }
            return Coupon(
                name = name,
                discountType = discountType,
                discountValue = discountValue
            )
        }
    }

    fun calculateDiscount(totalAmount: Long): Long {
        return when (discountType) {
            DiscountType.FIXED -> minOf(discountValue, totalAmount)
            DiscountType.RATE -> (totalAmount * discountValue / 100)
        }
    }
}
