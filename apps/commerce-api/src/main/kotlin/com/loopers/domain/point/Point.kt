package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.domain.point.validation.PointValidator
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal

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

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "INT DEFAULT 0")
    var version: Long? = null

    companion object {
        fun create(userId: Long, amount: BigDecimal): Point {
            return Point(userId, Amount(amount))
        }
    }

    fun charge(chargeAmount: BigDecimal) {
        amount = amount.plus(chargeAmount)
    }

    fun validatePoint(useAmount: BigDecimal) {
        if (amount.value < useAmount) {
            throw CoreException(ErrorType.POINT_NOT_ENOUGH, "포인트가 부족합니다. 차감금액: $useAmount, 보유금액: $amount")
        }
    }

    fun use(useAmount: BigDecimal) {
        validatePoint(useAmount)
        amount = amount.minus(useAmount)
    }

    @JvmInline
    value class Amount(
        val value: BigDecimal,
    ) {
        init {
            PointValidator.validateMinAmount(value)
        }

        operator fun plus(amount: BigDecimal): Amount {
            return Amount(value.plus(amount))
        }

        operator fun minus(amount: BigDecimal): Amount {
            return Amount(value.minus(amount))
        }
    }
}
