package com.loopers.domain.metrics

interface ProductMetricsRepository {
    fun findByProductId(productId: Long): ProductMetrics?
    fun findByProductIdWithLock(productId: Long): ProductMetrics?
    fun save(productMetrics: ProductMetrics): ProductMetrics
}
