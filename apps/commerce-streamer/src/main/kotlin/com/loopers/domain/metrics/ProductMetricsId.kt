package com.loopers.domain.metrics

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.time.LocalDate

/**
 * ProductMetrics 복합키
 *
 * @property productId 상품 ID
 * @property metricDate 메트릭 집계 일자
 */
@Embeddable
data class ProductMetricsId(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "metric_date", nullable = false)
    val metricDate: LocalDate,
) : Serializable
