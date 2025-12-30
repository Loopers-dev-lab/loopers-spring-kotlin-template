package com.loopers.application.ranking

import com.loopers.application.ranking.dto.RankingResult
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class RankingFacade(
    private val rankingService: RankingService,
    private val rankingAggregator: RankingAggregator,
) {

    @Transactional(readOnly = true)
    fun getRankings(period: RankingPeriod, date: String, pageable: Pageable): Page<RankingResult.RankedInfo> {
        val scorePage = rankingService.getRankingScores(period, date, pageable)

        if (scorePage.isEmpty) {
            return PageImpl(emptyList(), pageable, scorePage.totalElements)
        }

        val startRank = pageable.offset + 1L
        val rankingItems = rankingAggregator.aggregate(scorePage.content, startRank)

        return PageImpl(rankingItems, pageable, scorePage.totalElements)
    }

    @Transactional(readOnly = true)
    fun getProductRank(productId: Long): RankingResult.RankInfo? {
        val dateKey = rankingService.todayDateKey()
        val rank = rankingService.getRank(dateKey, productId) ?: return null
        val score = rankingService.getScore(dateKey, productId) ?: return null

        return RankingResult.RankInfo(score = score, rank = rank + 1L)
    }
}
