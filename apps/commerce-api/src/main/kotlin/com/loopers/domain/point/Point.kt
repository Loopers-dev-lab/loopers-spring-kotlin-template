package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "loopers_point")
class Point(
    @Column(unique = true)
    val userId: Long,

    @Column
    var amount: Long,
) : BaseEntity() {

    init {
        if (amount < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "포인트는 0 이상이어야 합니다.")
        }
    }

    companion object {
        fun of(userId: Long, initialAmount: Long = 0L): Point {
            return Point(
                userId = userId,
                amount = initialAmount,
            )
        }
    }

    fun charge(chargeAmount: Long) {
        if (chargeAmount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "충전 금액은 0보다 커야 합니다.")
        }
        this.amount += chargeAmount
    }
}
