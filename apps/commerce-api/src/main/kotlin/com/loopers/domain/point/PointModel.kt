package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "points")
class PointModel(userId: Long, balance: Long) : BaseEntity() {

    @Column
    var userId: Long = userId
        protected set

    @Column
    var balance: Long = balance
        protected set

    init {
        require(balance >= 0) {
            "포인트는 0 이상이어야 합니다."
        }
    }

    fun charge(amount: Long): Long {
        require(amount > 0) {
            "충전 금액은 0보다 커야 합니다."
        }

        balance += amount
        return balance
    }
}
