package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal

@Embeddable
data class Price(
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING)
    val currency: Currency = Currency.KRW,
) : Comparable<Price> {
    init {
        if (amount < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.")
        }
    }

    operator fun plus(other: Price): Price {
        if (this.currency != other.currency) {
            throw CoreException(ErrorType.BAD_REQUEST, "통화가 다른 가격은 더할 수 없습니다.")
        }
        return Price(this.amount + other.amount, this.currency)
    }

    operator fun times(multiplier: Int): Price = Price(this.amount * BigDecimal(multiplier), this.currency)

    operator fun unaryPlus(): Price = this

    operator fun unaryMinus(): Price = Price(-this.amount, this.currency)

    override fun compareTo(other: Price): Int {
        if (this.currency != other.currency) {
            throw CoreException(ErrorType.BAD_REQUEST, "통화가 다른 가격은 비교할 수 없습니다.")
        }
        return this.amount.compareTo(other.amount)
    }
}
