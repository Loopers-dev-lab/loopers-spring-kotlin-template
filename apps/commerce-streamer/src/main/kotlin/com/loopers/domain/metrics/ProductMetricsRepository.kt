package com.loopers.domain.metrics

import java.time.LocalDate

interface ProductMetricsRepository {
    fun findByProductIdAndMetricDate(productId: Long, metricDate: LocalDate): ProductMetrics?
    fun findByProductIdAndMetricDateWithLock(productId: Long, metricDate: LocalDate): ProductMetrics?
    fun save(productMetrics: ProductMetrics): ProductMetrics
    fun findByProductIdAndMetricDateBetween(
        productId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ProductMetrics>
}
