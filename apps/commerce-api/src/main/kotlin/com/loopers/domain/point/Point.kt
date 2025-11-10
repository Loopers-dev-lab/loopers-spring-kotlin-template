package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "loopers_point")
class Point(
    @Column(unique = true)
    val userId: Long,

    @Column(nullable = false, precision = 10, scale = 2)
    var balance: BigDecimal,
) : BaseEntity() {

    init {
        validateBalance(balance)
    }

    companion object {
        fun of(userId: Long, initialBalance: BigDecimal = BigDecimal.ZERO): Point {
            return Point(
                userId = userId,
                balance = initialBalance,
            )
        }
    }

    fun charge(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "충전 금액은 0보다 커야 합니다.")
        }
        this.balance = this.balance.add(amount)
    }

    fun deduct(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "차감 금액은 0보다 커야 합니다.")
        }
        validateBalance(this.balance.subtract(amount))
        this.balance = this.balance.subtract(amount)
    }

    fun isBalanceSufficient(amount: BigDecimal): Boolean {
        return this.balance >= amount
    }

    private fun validateBalance(balance: BigDecimal) {
        if (balance < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "포인트 잔액은 0 이상이어야 합니다.")
        }
    }
}
