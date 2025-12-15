package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetrics
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ProductMetricsJpaRepository : JpaRepository<ProductMetrics, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pm FROM ProductMetrics pm WHERE pm.productId = :productId")
    fun findByProductIdWithLock(productId: Long): ProductMetrics?
}
