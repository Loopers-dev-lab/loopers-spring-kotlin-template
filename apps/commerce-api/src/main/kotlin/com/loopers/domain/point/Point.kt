package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "point",
    indexes = [
        Index(name = "idx_point_user_id", columnList = "ref_user_id"),
    ],
)
class Point(
    @Embedded
    var amount: Amount,

    @Column(name = "ref_user_id", nullable = false)
    val userId: Long,
) : BaseEntity() {

    companion object {
        fun init(userId: Long): Point {
            return Point(
                amount = Amount(0),
                userId = userId,
            )
        }

        fun create(amount: Long, userId: Long): Point {
            return Point(
                amount = Amount(amount),
                userId = userId,
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
