package com.loopers.domain.ranking.dto

/**
 * 주간 상품 메트릭 날짜별 집계 DTO
 *
 * Reader가 날짜별로 집계한 메트릭 값 (7개 레코드 = 7일치)
 *
 * @property productId 상품 ID
 * @property daysFromEnd 주간 종료일로부터의 일수 (0~6)
 * @property viewCount 조회수
 * @property likeCount 좋아요수
 * @property soldCount 판매수
 */
data class ProductWeeklyMetricsAggregate(
    val productId: Long,
    val daysFromEnd: Int,
    val viewCount: Long,
    val likeCount: Long,
    val soldCount: Long,
)
