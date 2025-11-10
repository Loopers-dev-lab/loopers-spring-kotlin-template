package com.loopers.domain.point

interface PointTransactionRepository {
    fun save(transaction: PointTransaction): PointTransaction
    fun findByUserId(userId: Long): List<PointTransaction>
    fun findByOrderId(orderId: Long): List<PointTransaction>
}
