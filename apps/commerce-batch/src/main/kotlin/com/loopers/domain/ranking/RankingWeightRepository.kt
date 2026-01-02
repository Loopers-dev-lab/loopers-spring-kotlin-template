package com.loopers.domain.ranking

/**
 * RankingWeight Repository 인터페이스
 *
 * - 가중치 조회만 지원 (읽기 전용)
 * - infrastructure/ranking에서 구현
 */
interface RankingWeightRepository {
    fun findLatest(): RankingWeight?
}
