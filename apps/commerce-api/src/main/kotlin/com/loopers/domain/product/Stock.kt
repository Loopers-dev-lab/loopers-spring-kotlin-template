package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Stock(
    @Column(name = "amount", nullable = false)
    val amount: Int,
) {
    init {
        if (amount < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.")
        }
    }

    companion object {
        fun of(amount: Int): Stock {
            return Stock(amount)
        }

        fun create(amount: Int = 0): Stock = of(
            amount = amount,
        )
    }

    fun increase(amount: Int): Stock {
        if (amount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고 증가량은 0보다 커야 합니다.")
        }
        return copy(amount = this.amount + amount)
    }

    fun decrease(amount: Int): Stock {
        if (amount <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고 감소량은 0보다 커야 합니다.")
        }
        if (this.amount < amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.")
        }

        return copy(amount = this.amount - amount)
    }
}
