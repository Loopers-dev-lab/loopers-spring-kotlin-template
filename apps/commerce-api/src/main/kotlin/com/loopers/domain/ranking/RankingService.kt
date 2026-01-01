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
     * 랭킹을 조회한다.
     * Fallback 정책: offset=0이고 현재 버킷이 비어있으면 이전 period 버킷을 한 번 조회한다.
     *
     * @param command 조회 명령
     * @return ProductRanking 리스트 (hasNext 판단을 위해 limit + 1개 포함 가능)
     */
    @Transactional(readOnly = true)
    fun findRankings(command: RankingCommand.FindRankings): List<ProductRanking> {
        val query = command.toQuery()
        val rankings = productRankingReader.findTopRankings(query)

        // Fallback: if empty AND first page (offset=0), try previous period
        if (rankings.isEmpty() && query.offset == 0L) {
            return productRankingReader.findTopRankings(query.previousPeriod())
        }

        return rankings
    }
}
