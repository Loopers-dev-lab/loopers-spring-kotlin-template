package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductMetrics
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface ProductMetricsJpaRepository : JpaRepository<ProductMetrics, Long> {
    fun findByProductId(productId: Long): ProductMetrics?

    /**
     * 비관적 락을 사용하여 ProductMetrics를 조회
     * 동시성 제어를 위해 DB 레벨에서 행 잠금 획득
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pm FROM ProductMetrics pm WHERE pm.productId = :productId")
    fun findByProductIdWithLock(productId: Long): ProductMetrics?
}
