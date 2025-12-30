package com.loopers.domain.productMetric

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository

interface ProductMetricRepository {

    fun findByDateTimeBetween(
        startDateTime: String,
        endDateTime: String,
        pageable: Pageable,
    ): Page<ProductMetric>

    /**
     * Spring Batch RepositoryItemReader에서 사용하기 위한 JpaRepository 반환
     */
    fun getJpaRepository(): PagingAndSortingRepository<ProductMetric, Long>
}

