package com.loopers.domain.ranking

import java.math.BigDecimal
import java.time.Instant

/**
 * Batch command for metric ingestion
 *
 * 여러 메트릭을 한 번에 처리하기 위한 배치 커맨드
 */
data class AccumulateMetricsCommand(
    val items: List<Item>,
) {
    data class Item(
        val productId: Long,
        val statHour: Instant,
        val viewDelta: Long = 0,
        val likeCreatedDelta: Long = 0,
        val likeCanceledDelta: Long = 0,
        val orderAmountDelta: BigDecimal = BigDecimal.ZERO,
    )
}
