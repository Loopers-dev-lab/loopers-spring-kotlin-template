package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductHourlyMetric
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface ProductHourlyMetricJpaRepository : JpaRepository<ProductHourlyMetric, Long> {

    fun findAllByStatHour(statHour: Instant): List<ProductHourlyMetric>

    fun findAllByStatHourBetween(startHour: Instant, endHour: Instant): List<ProductHourlyMetric>
}
