package com.loopers.domain.ranking

interface RankingWeightRepository {
    fun findLatest(): RankingWeight?
    fun save(rankingWeight: RankingWeight): RankingWeight
}
