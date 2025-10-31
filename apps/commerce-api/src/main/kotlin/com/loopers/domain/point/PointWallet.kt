package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class PointWallet(
    val userId: Long,
    private val txs: MutableList<PointTransaction> = mutableListOf(),
) {
    companion object {
        fun of(userId: Long, txs: MutableList<PointTransaction> = mutableListOf()) = PointWallet(
            userId = userId,
            txs = txs,
        )
    }

    init {
        if (userId < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "유저 아이디는 음수가 될 수 없습니다.")
        }
    }

    fun addTransaction(transaction: PointTransaction) {
        if (transaction.userId != userId) {
            throw CoreException(ErrorType.BAD_REQUEST, "id가 $userId 와 ${transaction.userId} 가 달라 충전이 불가능합니다.")
        }

        txs.add(transaction)
    }

    fun aggregateBalance(): Money {
        return txs.fold(Money.ZERO_KRW) { acc, transaction ->
            acc.plus(transaction.amount)
        }
    }

    fun transactions(): List<PointTransaction> {
        return txs.toList()
    }
}
