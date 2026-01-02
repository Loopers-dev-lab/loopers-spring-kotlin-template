package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingWeight
import com.loopers.domain.ranking.RankingWeightRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class RankingWeightRdbRepository(
    private val rankingWeightJpaRepository: RankingWeightJpaRepository,
) : RankingWeightRepository {

    @Transactional(readOnly = true)
    override fun findLatest(): RankingWeight? {
        return rankingWeightJpaRepository.findLatest()
    }
}
