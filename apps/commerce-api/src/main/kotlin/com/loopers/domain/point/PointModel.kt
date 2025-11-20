package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.domain.common.vo.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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

    fun charge(balance: Money): Money {
        require(balance.amount > BigDecimal.ZERO) { "충전 금액은 0보다 커야 합니다." }

        this.balance += balance
        return this.balance
    }

    fun pay(request: Money): Money {
        if (request.amount <= BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "사용 금액은 0보다 커야 합니다.")
        }

        if (this.balance.amount < request.amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "잔액이 부족합니다.")
        }

        this.balance -= request
        return this.balance
    }
}
