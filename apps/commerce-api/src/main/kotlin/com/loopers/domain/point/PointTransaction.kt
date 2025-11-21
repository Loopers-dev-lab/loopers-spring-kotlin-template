package com.loopers.domain.point

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "loopers_point_transaction")
class PointTransaction(
    @Column(nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val transactionType: TransactionType,

    @Column(nullable = false, precision = 10, scale = 2)
    val amount: BigDecimal,

    @Column(nullable = false, precision = 10, scale = 2)
    val balanceBefore: BigDecimal,

    @Column(nullable = false, precision = 10, scale = 2)
    val balanceAfter: BigDecimal,

    @Column(length = 255)
    val description: String? = null,

    @Column
    val orderId: Long? = null,
) : BaseEntity() {

    init {
        validateAmount(amount)
        validateBalance(balanceBefore)
        validateBalance(balanceAfter)
    }

    companion object {
        fun of(
            userId: Long,
            transactionType: TransactionType,
            amount: BigDecimal,
            balanceBefore: BigDecimal,
            balanceAfter: BigDecimal,
            description: String? = null,
            orderId: Long? = null,
        ): PointTransaction {
            return PointTransaction(
                userId = userId,
                transactionType = transactionType,
                amount = amount,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                description = description,
                orderId = orderId,
            )
        }
    }

    private fun validateAmount(amount: BigDecimal) {
        if (amount < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "거래 금액은 0 이상이어야 합니다.")
        }
    }

    private fun validateBalance(balance: BigDecimal) {
        if (balance < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "잔액은 0 이상이어야 합니다.")
        }
    }

    enum class TransactionType(val description: String) {
        CHARGE("충전"),
        USE("사용"),
        REFUND("환불"),
        CANCEL("취소"),
    }
}
