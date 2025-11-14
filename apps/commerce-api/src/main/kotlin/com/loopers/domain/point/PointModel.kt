package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.domain.common.vo.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "points")
class PointModel(
    @Column
    val refUserId: Long,

    @Column
    var balance: Money,
) : BaseEntity() {

    fun charge(amount: Money): Money {
        require(amount.amount > BigDecimal.ZERO) {
            "충전 금액은 0보다 커야 합니다."
        }

        this.balance += amount
        return balance
    }

    fun pay(amount: Money): Money {
        require(amount.amount > BigDecimal.ZERO) {
            "사용 금액은 0보다 커야 합니다."
        }
        require(this.balance.amount >= amount.amount) {
            "잔액이 부족합니다."
        }
        this.balance -= amount
        return balance
    }
}
