package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.UserId
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
        this.amount += Amount(amount)
    }
}
