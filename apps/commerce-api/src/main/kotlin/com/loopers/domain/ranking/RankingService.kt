package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class RankingService(
    private val rankingWeightRepository: RankingWeightRepository,
    private val productRankingReader: ProductRankingReader,
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

    /**
     * 랭킹을 조회한다 (폴백 로직 포함)
     *
     * @param query 조회 조건
     * @return ProductRanking 리스트 (hasNext 판단을 위해 limit + 1개 포함 가능)
     */
    @Transactional(readOnly = true)
    fun findRankings(query: RankingQuery): List<ProductRanking> {
        val rankings = productRankingReader.findTopRankings(query)

        // Fallback: 현재 버킷이 비어있고, 첫 페이지이고, fallbackKey가 있으면
        if (rankings.isEmpty() && query.offset == 0L && query.fallbackKey != null) {
            val fallbackQuery = query.copy(
                bucketKey = query.fallbackKey,
                fallbackKey = null,
            )
            return productRankingReader.findTopRankings(fallbackQuery)
        }

        return rankings
    }
}
