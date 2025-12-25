package com.loopers.domain.productMetric

interface ProductMetricRepository {

    fun findByRefProductId(productId: Long): ProductMetricModel?

    fun save(metric: ProductMetricModel): ProductMetricModel

    fun findAll(): List<ProductMetricModel>

    fun findByRefProductIdIn(productIds: Set<Long>): List<ProductMetricModel>
}
