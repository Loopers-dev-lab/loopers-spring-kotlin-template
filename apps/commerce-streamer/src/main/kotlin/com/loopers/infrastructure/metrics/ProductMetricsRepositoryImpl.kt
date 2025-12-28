package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetrics
import com.loopers.domain.metrics.ProductMetricsRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class ProductMetricsRepositoryImpl(
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
) : ProductMetricsRepository {

    override fun findByProductIdAndMetricDate(productId: Long, metricDate: LocalDate): ProductMetrics? {
        return productMetricsJpaRepository.findByIdProductIdAndIdMetricDate(productId, metricDate)
    }

    override fun findByProductIdAndMetricDateWithLock(productId: Long, metricDate: LocalDate): ProductMetrics? {
        return productMetricsJpaRepository.findByProductIdAndMetricDateWithLock(productId, metricDate)
    }

    override fun save(productMetrics: ProductMetrics): ProductMetrics {
        return productMetricsJpaRepository.save(productMetrics)
    }

    override fun findByProductIdAndMetricDateBetween(
        productId: Long,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ProductMetrics> {
        return productMetricsJpaRepository.findByIdProductIdAndIdMetricDateBetween(
            productId,
            startDate,
            endDate,
        )
    }
}
