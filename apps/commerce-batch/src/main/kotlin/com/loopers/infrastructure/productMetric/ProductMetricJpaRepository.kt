package com.loopers.infrastructure.productMetric

import com.loopers.domain.productMetric.ProductMetric
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductMetricJpaRepository : JpaRepository<ProductMetric, Long> {

    fun findByDateTimeBetween(
        startDateTime: String,
        endDateTime: String,
        pageable: Pageable,
    ): Page<ProductMetric>
}
