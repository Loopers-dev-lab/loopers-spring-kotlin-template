package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.domain.point.validation.PointValidator
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

    fun charge(amount: Int) {
        this.amount += amount
    }

    @JvmInline
    value class Amount(
        val value: Int,
    ) {
        init {
            PointValidator.validateMinAmount(value)
        }

        operator fun plus(amount: Int): Amount {
            return Amount(this.value + amount)
        }
    }
}
