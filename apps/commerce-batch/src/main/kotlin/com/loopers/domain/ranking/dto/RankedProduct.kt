package com.loopers.domain.ranking.dto

/**
 * 랭킹 계산된 상품 DTO
 *
 * @property productId 상품 ID
 * @property finalScore 최종 점수 (기본 점수 × 날짜별 감쇠 가중치)
 */
data class RankedProduct(
    val productId: Long,
    val finalScore: Double,
)
