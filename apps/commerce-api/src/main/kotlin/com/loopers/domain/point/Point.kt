package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.domain.point.validation.PointValidator
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "point")
class Point protected constructor(
    userId: Long,
    amount: Amount,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false, unique = true)
    var userId: Long = userId
        protected set

    @Column(name = "amount", nullable = false)
    var amount: Amount = amount
        protected set

    companion object {
        fun create(userId: Long, amount: Int): Point {
            return Point(userId, Amount(amount))
        }
    }

    fun charge(chargeAmount: Int) {
        amount = amount.plus(chargeAmount)
    }

    fun use(useAmount: Int) {
        if (amount.value < useAmount) {
            throw CoreException(ErrorType.POINT_NOT_ENOUGH, "포인트가 부족합니다. 차감금액: $useAmount, 보유금액: $amount")
        }
        amount = amount.minus(useAmount)
    }

    @JvmInline
    value class Amount(
        val value: Int,
    ) {
        init {
            PointValidator.validateMinAmount(value)
        }

        operator fun plus(amount: Int): Amount {
            return Amount(value + amount)
        }

        operator fun minus(amount: Int): Amount {
            return Amount(value - amount)
        }
    }
}
