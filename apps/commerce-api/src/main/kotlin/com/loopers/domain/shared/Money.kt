package com.loopers.domain.shared

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Money(
    @Column(name = "amount", nullable = false)
    val amount: Long,
) {
    init {
        if (amount < 0) {
            throw CoreException(ErrorType.INVALID_POINT_AMOUNT, "금액은 0이상이어야 합니다. 입력값: $amount")
        }
    }

    fun plus(other: Money): Money = Money(this.amount + other.amount)

    fun minus(other: Money): Money {
        if (amount < other.amount) {
            throw CoreException(ErrorType.INVALID_POINT_AMOUNT, "금액이 부족합니다. 현재: $amount, 차감: ${other.amount}")
        }
        return Money(this.amount - other.amount)
    }

    fun multiply(quantity: Int): Money {
        if (quantity < 0) {
            throw CoreException(ErrorType.INVALID_QUANTITY, "곱할 수량은 0 이상이어야 합니다. 입력값: $quantity")
        }
        return Money(this.amount * quantity)
    }

    fun isGreaterThanOrEqual(other: Money): Boolean = this.amount >= other.amount

    companion object {
        fun of(amount: Long) = Money(amount)
        fun zero() = Money(0L)
    }
}
