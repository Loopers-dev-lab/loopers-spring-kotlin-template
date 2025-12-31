package com.loopers.domain.ranking

import java.math.BigDecimal
import java.time.Instant

/**
 * ProductHourlyMetricRow - 배치 Upsert를 위한 데이터 클래스
 *
 * - RankingAggregationService에서 flush 시 사용
 * - DB에 저장할 집계 데이터를 담음
 */
data class ProductHourlyMetricRow(
    val productId: Long,
    val statHour: Instant,
    val viewCount: Long,
    val likeCount: Long,
    val orderAmount: BigDecimal,
)
