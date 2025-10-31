package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "point_transactions")
class PointTransaction(
    userId: Long,
    transactionType: PointTransactionType,
    amount: Money,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    var transactionType: PointTransactionType = transactionType
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "amount", nullable = false))
    var amount: Money = amount
        private set

    companion object {
        fun of(userId: Long, transactionType: PointTransactionType, amount: Money): PointTransaction {
            return PointTransaction(userId, transactionType, amount)
        }

        fun charge(userId: Long, amount: Money): PointTransaction {
            if (amount <= Money.ZERO_KRW) {
                throw CoreException(ErrorType.BAD_REQUEST, "충전은 양수여야 합니다.")
            }

            return PointTransaction(userId, PointTransactionType.CHARGE, amount)
        }
    }

    init {
        if (userId < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "유저 아이디는 음수가 될 수 없습니다.")
        }
    }
}
