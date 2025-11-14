package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Point(
    @Column(name = "point", nullable = false)
    val amount: Long
) {
    init {
        if (amount < 0) {
            throw CoreException(ErrorType.INVALID_POINT_AMOUNT, "포인트는 0 이상이어야 합니다. 입력값: $amount")
        }
    }

    fun charge(amount: Long): Point {
        if (amount <= 0) {
            throw CoreException(ErrorType.INVALID_POINT_AMOUNT, "충전할 포인트는 0보다 커야 합니다. 입력값: $amount")
        }
        return Point(this.amount + amount)
    }

    fun use(amount: Long): Point {
        if (amount <= 0) {
            throw CoreException(ErrorType.INVALID_POINT_AMOUNT, "사용할 포인트는 0보다 커야 합니다. 입력값: $amount")
        }
        if (this.amount < amount) {
            throw CoreException(ErrorType.INSUFFICIENT_POINT, "포인트가 부족합니다. 보유: ${this.amount}, 필요: $amount")
        }
        return Point(this.amount - amount)
    }

    fun hasEnoughBalance(amount: Long): Boolean = this.amount >= amount


    companion object {
        fun of(amount: Long) = Point(amount)
        fun zero() = Point(0L)
    }


}
