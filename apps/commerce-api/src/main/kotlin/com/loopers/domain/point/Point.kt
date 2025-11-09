package com.loopers.domain.point

import com.loopers.domain.order.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.AttributeOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.ZonedDateTime

@Entity
@Table(name = "points")
class Point(
    @Id
    @Column(name = "user_id")
    val userId: Long,

    balance: Money,
) {
    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "amount", column = Column(name = "balance", nullable = false, precision = 15, scale = 2)),
        AttributeOverride(name = "currency", column = Column(name = "currency", nullable = false, length = 3)),
    )
    var balance: Money = balance
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersist() {
        val now = ZonedDateTime.now()
        updatedAt = now
    }

    @PreUpdate
    private fun preUpdate() {
        updatedAt = ZonedDateTime.now()
    }

    fun charge(amount: Money) {
        if (amount.amount <= BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "충전 금액은 0보다 커야 합니다.")
        }
        this.balance = this.balance.add(amount)
    }

    fun deduct(amount: Money) {
        if (amount.amount <= BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "차감 금액은 0보다 커야 합니다.")
        }
        if (!canDeduct(amount)) {
            throw CoreException(ErrorType.BAD_REQUEST, "포인트 부족: 현재 잔액 ${balance.amount}, 차감 요청 ${amount.amount}")
        }
        this.balance = this.balance.subtract(amount)
    }

    fun canDeduct(amount: Money): Boolean = this.balance.isGreaterThanOrEqual(amount)
}
