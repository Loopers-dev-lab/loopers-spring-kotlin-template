package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(
    name = "point_accounts",
)
class PointAccount(
    userId: Long,
    balance: Money,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        private set

    @Column(name = "balance", nullable = false)
    var balance: Money = balance
        private set

    companion object {
        fun create(userId: Long): PointAccount {
            return PointAccount(
                userId = userId,
                balance = Money.ZERO_KRW,
            )
        }

        fun of(userId: Long, balance: Money): PointAccount {
            return PointAccount(
                userId = userId,
                balance = balance,
            )
        }
    }

    fun charge(amount: Money) {
        if (amount <= Money.ZERO_KRW) {
            throw CoreException(ErrorType.BAD_REQUEST, "충전은 양수여야 합니다.")
        }

        this.balance = amount.plus(this.balance)
    }
}
