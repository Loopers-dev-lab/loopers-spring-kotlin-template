package com.loopers.support.values

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.math.BigDecimal

@Embeddable
data class Money(
    @Column(name = "amount", nullable = false, scale = 2, precision = 15)
    val amount: BigDecimal,
) : Comparable<Money> {

    companion object {
        val ZERO_KRW = Money(BigDecimal.ZERO)

        fun krw(amount: BigDecimal): Money {
            return Money(amount)
        }

        fun krw(amount: Long): Money {
            return Money(BigDecimal.valueOf(amount))
        }

        fun krw(amount: Int): Money {
            return Money(BigDecimal.valueOf(amount.toLong()))
        }
    }

    /**
     * `Money` 객체를 다른 객체와 비교합니다.
     * 비교는 두 사례의 금액을 기준으로 합니다
     *
     * @param other `Money` 인스턴스와 비교할 다른 객체
     * @return -1, 0, or 1. `Money`가 금액적으로 `other` 보다 작거나, 동등하고, 클 때
     */
    override fun compareTo(other: Money): Int {
        return amount.compareTo(other.amount)
    }

    operator fun plus(other: Money): Money {
        return Money(amount.add(other.amount))
    }

    operator fun minus(other: Money): Money {
        return Money(amount.subtract(other.amount))
    }

    operator fun times(multiplier: Int): Money {
        return Money(amount.multiply(BigDecimal.valueOf(multiplier.toLong())))
    }

    /**
     * `Money` 객체를 다른 객체와 비교하여 동등성을 판단합니다.
     * 두 `Money` 객체는 금액이 동일할 경우 동등하다고 간주됩니다.
     *
     * @param other `Money` 인스턴스와 비교할 다른 객체
     * @return 객체가 동일하거나 `other`가 동일한 금액을 가진 `Money` 인스턴스인 경우 `true`,
     * 그렇지 않은 경우 `false`
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Money) return false
        return amount.compareTo(other.amount) == 0
    }

    override fun hashCode(): Int {
        return amount.stripTrailingZeros().hashCode()
    }
}
