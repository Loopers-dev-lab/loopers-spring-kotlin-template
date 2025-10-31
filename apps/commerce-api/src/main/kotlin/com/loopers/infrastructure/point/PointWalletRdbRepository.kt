package com.loopers.infrastructure.point

import com.loopers.domain.point.PointWallet
import com.loopers.domain.point.PointWalletRepository
import org.springframework.stereotype.Component

@Component
class PointWalletRdbRepository(
    private val pointTransactionJpaRepository: PointTransactionJpaRepository,
) : PointWalletRepository {
    override fun getByUserId(userId: Long): PointWallet {
        val txs = pointTransactionJpaRepository.findAllByUserId(userId)
        return PointWallet.of(userId, txs.toMutableList())
    }

    override fun save(pointWallet: PointWallet): PointWallet {
        val savedTransactions = pointTransactionJpaRepository.saveAll(pointWallet.transactions())
        return PointWallet.of(pointWallet.userId, savedTransactions.toMutableList())
    }
}
