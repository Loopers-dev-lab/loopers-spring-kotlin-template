package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Stock(
    @Column(name = "stock", nullable = false)
    val quantity: Int,
) {
    init {
        if (quantity < 0) {
            throw CoreException(ErrorType.INVALID_STOCK, )
        }
    }

    fun decrease(amount: Int): Stock {
        if (amount <= 0) {
            throw CoreException(ErrorType.INVALID_STOCK, "차감할 수량은 0보다 커야 합니다. 입력값: $amount")
        }
        if (!hasEnough(amount)) {
            throw CoreException(ErrorType.INSUFFICIENT_STOCK, "재고가 부족합니다. 현재: $quantity, 필요: $amount")
        }

        return Stock(quantity - amount)
    }

    fun hasEnough(required: Int): Boolean = quantity >= required

    companion object {
        fun of(quantity: Int) = Stock(quantity)
        fun zero() = Stock(0)
    }
}
