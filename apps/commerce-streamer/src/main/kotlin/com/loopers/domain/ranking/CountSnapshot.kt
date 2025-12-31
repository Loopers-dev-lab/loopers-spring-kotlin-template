package com.loopers.domain.ranking

import java.math.BigDecimal

/**
 * CountSnapshot - 불변 카운트 스냅샷
 *
 * - ProductHourlyMetric으로부터 생성되는 불변 스냅샷
 * - 점수 계산을 위한 집계 데이터 보관
 */
data class CountSnapshot(
    val views: Long,
    val likes: Long,
    val orderCount: Long,
    val orderAmount: BigDecimal,
)
