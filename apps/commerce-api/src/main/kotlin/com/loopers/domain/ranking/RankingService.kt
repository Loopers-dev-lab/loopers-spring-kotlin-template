package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class RankingService(
    private val rankingWeightRepository: RankingWeightRepository,
) {
    @Transactional(readOnly = true)
    fun findWeight(): RankingWeight {
        return rankingWeightRepository.findLatest()
            ?: RankingWeight.fallback()
    }

    @Transactional
    fun updateWeight(
        viewWeight: BigDecimal,
        likeWeight: BigDecimal,
        orderWeight: BigDecimal,
    ): RankingWeight {
        val existingWeight = rankingWeightRepository.findLatest()

        val newWeight = existingWeight?.createNext(
            viewWeight = viewWeight,
            likeWeight = likeWeight,
            orderWeight = orderWeight,
        ) ?: RankingWeight.create(
            viewWeight = viewWeight,
            likeWeight = likeWeight,
            orderWeight = orderWeight,
        )

        return rankingWeightRepository.save(newWeight)
    }
}
