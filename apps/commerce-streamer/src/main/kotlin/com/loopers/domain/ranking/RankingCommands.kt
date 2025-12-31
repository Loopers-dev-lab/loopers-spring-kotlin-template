package com.loopers.domain.ranking

import java.math.BigDecimal
import java.time.Instant

/**
 * MetricType - 랭킹 집계를 위한 메트릭 종류
 *
 * - 어떤 카운터를 증감할지 결정
 * - 이벤트 타입 식별에 사용
 */
enum class MetricType {
    VIEW,
    LIKE_CREATED,
    LIKE_CANCELED,
    ORDER_PAID,
}

/**
 * Command for VIEW event
 */
data class AccumulateViewMetricCommand(
    val eventId: String,
    val productId: Long,
    val occurredAt: Instant,
)

/**
 * Command for LIKE_CREATED event
 */
data class AccumulateLikeCreatedMetricCommand(
    val eventId: String,
    val productId: Long,
    val occurredAt: Instant,
)

/**
 * Command for LIKE_CANCELED event
 */
data class AccumulateLikeCanceledMetricCommand(
    val eventId: String,
    val productId: Long,
    val occurredAt: Instant,
)

/**
 * Command for ORDER_PAID event
 */
data class AccumulateOrderPaidMetricCommand(
    val eventId: String,
    val items: List<Item>,
    val occurredAt: Instant,
) {
    data class Item(
        val productId: Long,
        val orderAmount: BigDecimal,
    )
}

/**
 * Command for weight change triggered recalculation
 */
data class RecalculateScoresCommand(
    val eventId: String,
)
