package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "point_accounts",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_point_account_user",
            columnNames = ["user_id"],
        ),
    ],
)
class PointAccount(
    userId: Long,
    balance: Money,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "balance", nullable = false))
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

    fun deduct(amount: Money) {
        if (amount <= Money.ZERO_KRW) {
            throw CoreException(ErrorType.BAD_REQUEST, "차감은 양수여야 합니다.")
        }

        if (this.balance < amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다.")
        }

        this.balance = this.balance.minus(amount)
    }
}
