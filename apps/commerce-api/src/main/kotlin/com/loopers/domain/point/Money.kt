package com.loopers.domain.point

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.math.BigDecimal

@Embeddable
data class Money(
    @Column(name = "amount", nullable = false, scale = 2)
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

    override fun compareTo(other: Money): Int {
        return amount.compareTo(other.amount)
    }

    fun plus(other: Money): Money {
        return Money(amount.add(other.amount))
    }
}
