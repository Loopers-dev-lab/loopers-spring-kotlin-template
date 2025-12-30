package com.loopers.infrastructure.productMetric

import com.loopers.domain.productMetric.ProductMetric
import com.loopers.domain.productMetric.ProductMetricRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Component

@Component
class ProductMetricRepositoryImpl(
    private val productMetricJpaRepository: ProductMetricJpaRepository,
) : ProductMetricRepository {

    override fun findByDateTimeBetween(
        startDateTime: String,
        endDateTime: String,
        pageable: Pageable,
    ): Page<ProductMetric> =
        productMetricJpaRepository.findByDateTimeBetween(startDateTime, endDateTime, pageable)

    override fun getJpaRepository(): PagingAndSortingRepository<ProductMetric, Long> =
        productMetricJpaRepository
}

