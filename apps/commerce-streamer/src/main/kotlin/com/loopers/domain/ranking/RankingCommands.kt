package com.loopers.domain.ranking

import java.math.BigDecimal
import java.time.Instant

/**
 * MetricType - 랭킹 집계를 위한 메트릭 종류
 *
 * - MutableCounts에서 어떤 카운터를 증감할지 결정
 * - AggregationKey 생성 시 사용
 */
enum class MetricType {
    VIEW,
    LIKE_CREATED,
    LIKE_CANCELED,
    ORDER_PAID,
}

/**
 * AccumulateMetricCommand - 시간별 상품 메트릭 축적 Command
 *
 * - 버퍼에 적재되어 flush 시 DB/Redis에 반영
 * - occurredAt 기준으로 시간 버킷 결정
 */
data class AccumulateMetricCommand(
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val metricType: MetricType,
        val orderAmount: BigDecimal?,
        val occurredAt: Instant,
    )
}
