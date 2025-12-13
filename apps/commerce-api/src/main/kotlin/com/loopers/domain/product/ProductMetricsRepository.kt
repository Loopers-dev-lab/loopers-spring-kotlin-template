package com.loopers.domain.product

/**
 * Product Metrics Repository
 */
interface ProductMetricsRepository {
    fun save(productMetrics: ProductMetrics): ProductMetrics
    fun findByProductId(productId: Long): ProductMetrics?
    fun findOrCreateByProductId(productId: Long): ProductMetrics
}
