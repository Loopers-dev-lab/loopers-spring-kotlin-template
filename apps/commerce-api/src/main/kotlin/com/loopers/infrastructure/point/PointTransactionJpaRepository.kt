package com.loopers.infrastructure.point

import com.loopers.domain.point.PointTransaction
import org.springframework.data.jpa.repository.JpaRepository

interface PointTransactionJpaRepository : JpaRepository<PointTransaction, Long> {
    fun findAllByUserId(userId: Long): List<PointTransaction>
}
