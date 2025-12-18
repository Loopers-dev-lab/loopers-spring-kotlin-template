package com.loopers.infrastructure.productMetric

import com.loopers.domain.productMetric.ProductMetricModel
import org.springframework.data.jpa.repository.JpaRepository

interface ProductMetricJpaRepository : JpaRepository<ProductMetricModel, Long> {
    fun findByRefProductId(productId: Long): ProductMetricModel?
}
