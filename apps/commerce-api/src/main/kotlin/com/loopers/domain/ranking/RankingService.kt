package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class RankingService(
    private val rankingWeightRepository: RankingWeightRepository,
    private val productRankingReader: ProductRankingReader,
    private val rankingKeyGenerator: RankingKeyGenerator,
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
     * @param command 조회 명령
     * @return ProductRanking 리스트 (hasNext 판단을 위해 limit + 1개 포함 가능)
     */
    @Transactional(readOnly = true)
    fun findRankings(command: RankingCommand.FindRankings): List<ProductRanking> {
        val bucketKey = if (command.date != null) {
            rankingKeyGenerator.bucketKey(command.period, command.date)
        } else {
            rankingKeyGenerator.currentBucketKey(command.period)
        }

        val query = RankingQuery(
            period = command.period,
            bucketKey = bucketKey,
            offset = (command.page * command.size).toLong(),
            limit = command.size.toLong(),
        )

        val rankings = productRankingReader.findTopRankings(query)

        // Fallback: first page empty -> try previous bucket (only when no specific date)
        if (rankings.isEmpty() && query.offset == 0L && command.date == null) {
            val fallbackKey = rankingKeyGenerator.previousBucketKey(query.bucketKey)
            val fallbackQuery = query.copy(bucketKey = fallbackKey)
            return productRankingReader.findTopRankings(fallbackQuery)
        }
        return rankings
    }
}
