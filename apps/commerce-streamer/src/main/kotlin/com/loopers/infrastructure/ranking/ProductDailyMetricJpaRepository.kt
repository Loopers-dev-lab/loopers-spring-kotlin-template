package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductDailyMetric
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface ProductDailyMetricJpaRepository : JpaRepository<ProductDailyMetric, Long> {

    fun findByStatDateAndProductId(statDate: LocalDate, productId: Long): ProductDailyMetric?

    fun findAllByStatDate(statDate: LocalDate): List<ProductDailyMetric>
}
