package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "coupons")
class CouponModel(
    @Column
    val refUserId: Long,

    @Column
    val name: String,

    @Column
    @Enumerated(EnumType.STRING)
    val discountType: DiscountType,

    @Column
    val discountValue: BigDecimal,

    ) : BaseEntity() {

    @Column
    var isUsed: Boolean = false

    fun updateUsed() {
        this.isUsed = true
    }

    fun calculateDiscountPrice(orderAmount: BigDecimal): BigDecimal = discountType.calculateDiscountPrice(
        discountValue = this.discountValue,
        orderAmount = orderAmount,
    )

    companion object {
        fun create(refUserId: Long, name: String, discountType: DiscountType, discountValue: BigDecimal): CouponModel =
            CouponModel(refUserId, name, discountType, discountValue)
    }
}
