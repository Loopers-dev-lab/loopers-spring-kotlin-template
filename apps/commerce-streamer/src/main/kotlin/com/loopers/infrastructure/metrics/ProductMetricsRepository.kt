package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetrics
import org.springframework.data.jpa.repository.JpaRepository

interface ProductMetricsRepository : JpaRepository<ProductMetrics, Long> {
    fun findByProductId(productId: Long): ProductMetrics?
}
