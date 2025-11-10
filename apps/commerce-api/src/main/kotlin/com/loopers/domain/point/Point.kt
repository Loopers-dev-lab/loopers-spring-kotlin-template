package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.UserId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "point")
class Point(
    @Embedded
    var amount: Amount,

    @Embedded
    val userId: UserId,
) : BaseEntity() {

    companion object {
        fun create(amount: Long, userId: String): Point {
            return Point(
                amount = Amount(amount),
                userId = UserId(userId),
            )
        }
    }

    fun charge(amount: Long) {
        require(amount > 0) { "충전 금액은 0보다 커야 합니다." }
        this.amount += Amount(amount)
    }

    fun use(amount: Long) {
        require(amount > 0) { "사용 금액은 0보다 커야 합니다." }

        val useAmount = Amount(amount)

        if (this.amount.isZero() || this.amount.isLessThan(useAmount)) {
            throw CoreException(ErrorType.INSUFFICIENT_BALANCE)
        }

        this.amount -= useAmount
    }
}
