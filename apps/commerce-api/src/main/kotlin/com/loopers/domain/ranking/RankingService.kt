package com.loopers.domain.ranking

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class RankingService(
    private val rankingWeightRepository: RankingWeightRepository,
    private val eventPublisher: ApplicationEventPublisher,
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
        val rankingWeight = rankingWeightRepository.findLatest()
            ?: RankingWeight.create(
                viewWeight = viewWeight,
                likeWeight = likeWeight,
                orderWeight = orderWeight,
            )

        rankingWeight.update(
            viewWeight = viewWeight,
            likeWeight = likeWeight,
            orderWeight = orderWeight,
        )

        val savedWeight = rankingWeightRepository.save(rankingWeight)

        rankingWeight.pollEvents().forEach { eventPublisher.publishEvent(it) }

        return savedWeight
    }
}
