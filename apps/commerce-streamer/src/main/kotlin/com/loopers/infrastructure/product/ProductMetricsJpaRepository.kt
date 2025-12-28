package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductMetrics
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints

interface ProductMetricsJpaRepository : JpaRepository<ProductMetrics, Long> {
    fun findByProductId(productId: Long): ProductMetrics?

    /**
     * 비관적 락을 사용하여 ProductMetrics를 조회
     * 동시성 제어를 위해 DB 레벨에서 행 잠금 획득
     * 5초 타임아웃 설정으로 무한 블로킹 방지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
        value = [
            QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"),
        ],
    )
    @Query("SELECT pm FROM ProductMetrics pm WHERE pm.productId = :productId")
    fun findByProductIdWithLock(productId: Long): ProductMetrics?

    /**
     * 여러 상품 ID로 ProductMetrics 조회
     */
    fun findAllByProductIdIn(productIds: List<Long>): List<ProductMetrics>
}
