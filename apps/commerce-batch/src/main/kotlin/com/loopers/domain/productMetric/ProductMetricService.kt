package com.loopers.domain.productMetric

import org.springframework.batch.item.data.RepositoryItemReader
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductMetricService(
    private val productMetricRepository: ProductMetricRepository,
) {

    @Transactional(readOnly = true)
    fun findByDateTimeBetween(
        startDateTime: String,
        endDateTime: String,
        pageable: Pageable,
    ): Page<ProductMetric> =
        productMetricRepository.findByDateTimeBetween(startDateTime, endDateTime, pageable)

    fun createItemReader(
        startDateTime: String,
        endDateTime: String,
        pageSize: Int,
    ): RepositoryItemReader<ProductMetric> =
        RepositoryItemReaderBuilder<ProductMetric>()
            .name("productMetricReader")
            .repository(productMetricRepository.getJpaRepository())
            .methodName("findByDateTimeBetween")
            .arguments(listOf(startDateTime, endDateTime))
            .sorts(mapOf("id" to Sort.Direction.ASC))
            .pageSize(pageSize)
            .build()
}

