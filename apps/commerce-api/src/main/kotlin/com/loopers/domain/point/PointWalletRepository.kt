package com.loopers.domain.point

interface PointWalletRepository {
    fun getByUserId(userId: Long): PointWallet
    fun save(pointWallet: PointWallet): PointWallet
}
