package com.loopers.domain.order

import com.loopers.domain.product.Currency
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.math.BigDecimal

@Embeddable
data class Money(
    val amount: BigDecimal,
    @Enumerated(EnumType.STRING)
    val currency: Currency = Currency.KRW,
) {
    init {
        if (amount < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다.")
        }
    }

    fun add(other: Money): Money {
        if (this.currency != other.currency) {
            throw CoreException(ErrorType.BAD_REQUEST, "통화가 다른 금액은 더할 수 없습니다.")
        }
        return Money(this.amount + other.amount, this.currency)
    }

    fun subtract(other: Money): Money {
        if (this.currency != other.currency) {
            throw CoreException(ErrorType.BAD_REQUEST, "통화가 다른 금액은 뺄 수 없습니다.")
        }
        val result = this.amount - other.amount
        if (result < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "차감 결과가 음수가 될 수 없습니다.")
        }
        return Money(result, this.currency)
    }

    fun multiply(multiplier: Int): Money = Money(this.amount * BigDecimal(multiplier), this.currency)

    fun isGreaterThanOrEqual(other: Money): Boolean {
        if (this.currency != other.currency) {
            throw CoreException(ErrorType.BAD_REQUEST, "통화가 다른 금액은 비교할 수 없습니다.")
        }
        return this.amount >= other.amount
    }
}
