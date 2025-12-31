package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetrics
import com.loopers.domain.metrics.ProductMetricsId
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface ProductMetricsJpaRepository : JpaRepository<ProductMetrics, ProductMetricsId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pm FROM ProductMetrics pm WHERE pm.id.productId = :productId AND pm.id.metricDate = :metricDate")
    fun findByProductIdAndMetricDateWithLock(productId: Long, metricDate: LocalDate): ProductMetrics?

    fun findByIdProductIdAndIdMetricDate(productId: Long, metricDate: LocalDate): ProductMetrics?

    fun findByIdProductIdAndIdMetricDateBetween(
        productId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ProductMetrics>
}
