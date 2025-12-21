package com.loopers.infrastructure.productMetric

import com.loopers.domain.productMetric.ProductMetricModel
import com.loopers.domain.productMetric.ProductMetricRepository
import org.springframework.stereotype.Component

@Component
class ProductMetricRepositoryImpl(private val productMetricJpaRepository: ProductMetricJpaRepository) : ProductMetricRepository {

    override fun findByRefProductId(productId: Long): ProductMetricModel? = productMetricJpaRepository.findByRefProductId(
        productId,
    )

    override fun save(metric: ProductMetricModel): ProductMetricModel = productMetricJpaRepository.saveAndFlush(metric)
}
