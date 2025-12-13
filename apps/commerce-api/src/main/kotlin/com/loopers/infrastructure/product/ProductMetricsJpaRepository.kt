package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductMetrics
import org.springframework.data.jpa.repository.JpaRepository

interface ProductMetricsJpaRepository : JpaRepository<ProductMetrics, Long> {
    fun findByProductId(productId: Long): ProductMetrics?
}
