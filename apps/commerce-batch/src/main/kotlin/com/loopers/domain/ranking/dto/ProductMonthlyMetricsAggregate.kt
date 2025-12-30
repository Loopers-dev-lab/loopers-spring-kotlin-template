package com.loopers.domain.ranking.dto

/**
 * 월간 상품 메트릭 집계 DTO
 *
 * Reader가 GROUP BY로 집계한 원본 메트릭 값
 *
 * @property productId 상품 ID
 * @property totalViewCount 총 조회수
 * @property totalLikeCount 총 좋아요수
 * @property totalSoldCount 총 판매수
 */
data class ProductMonthlyMetricsAggregate(
    val productId: Long,
    val totalViewCount: Long,
    val totalLikeCount: Long,
    val totalSoldCount: Long,
)
