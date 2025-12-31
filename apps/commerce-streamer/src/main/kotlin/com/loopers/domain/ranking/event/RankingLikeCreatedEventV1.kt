package com.loopers.domain.ranking.event

/**
 * RankingLikeCreatedEventV1 - like.created.v1 이벤트의 Ranking Consumer Contract
 *
 * - 발행자: commerce-api (Like 도메인)
 * - 원본 이벤트에서 Ranking 집계에 필요한 필드만 정의
 */
data class RankingLikeCreatedEventV1(
    val productId: Long,
)
