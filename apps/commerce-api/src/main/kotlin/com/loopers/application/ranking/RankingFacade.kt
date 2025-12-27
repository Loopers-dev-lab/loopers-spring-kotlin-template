package com.loopers.application.ranking

import com.loopers.application.ranking.dto.RankingResult
import com.loopers.domain.ranking.RankingAggregator
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
    fun getRankings(date: String, pageable: Pageable): Page<RankingResult.RankedInfo> {
        val dateKey = rankingService.parseDateKey(date)
        val totalCount = rankingService.getTotalCount(dateKey)

        if (totalCount == 0L) {
            return Page.empty(pageable)
        }

        if (pageable.offset >= totalCount) {
            return PageImpl(emptyList(), pageable, totalCount)
        }

        val pageScores = rankingService.getPagedScores(dateKey, pageable.offset, pageable.pageSize)
        if (pageScores.isEmpty()) {
            return PageImpl(emptyList(), pageable, totalCount)
        }

        val startRank = pageable.offset + 1L
        val rankingItems = rankingAggregator.aggregate(pageScores, startRank)

        return PageImpl(rankingItems, pageable, totalCount)
    }

    @Transactional(readOnly = true)
    fun getProductRank(productId: Long): RankingResult.RankInfo? {
        val dateKey = rankingService.todayDateKey()
        val rank = rankingService.getRank(dateKey, productId) ?: return null
        val score = rankingService.getScore(dateKey, productId) ?: return null

        return RankingResult.RankInfo(score = score, rank = rank + 1L)
    }
}
