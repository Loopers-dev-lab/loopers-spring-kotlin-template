package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductHourlyMetric
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
interface ProductHourlyMetricJpaRepository : JpaRepository<ProductHourlyMetric, Long> {

    fun findByStatHourAndProductId(statHour: ZonedDateTime, productId: Long): ProductHourlyMetric?

    fun findAllByStatHour(statHour: ZonedDateTime): List<ProductHourlyMetric>
}
