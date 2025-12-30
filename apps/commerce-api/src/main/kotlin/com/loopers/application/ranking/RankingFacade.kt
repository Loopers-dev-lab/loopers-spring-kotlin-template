package com.loopers.application.ranking

import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingService
import com.loopers.domain.ranking.TimeWindow
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@Component
class RankingFacade(private val rankingService: RankingService) {
    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")
    }

    fun getRankingPage(
        window: TimeWindow,
        date: String?,
        page: Int,
        size: Int,
    ): RankingPageInfo {
        // 날짜 파라미터가 없으면 현재 날짜/시간 사용 (Asia/Seoul 기준)
        val timestamp = date ?: when (window) {
            TimeWindow.DAILY -> RankingKey.dateToString(LocalDate.now(ZONE))
            TimeWindow.HOURLY -> RankingKey.dateTimeToString(LocalDateTime.now(ZONE))
            TimeWindow.WEEKLY -> ProductRankWeekly.yearWeekToString(LocalDate.now(ZONE))
            TimeWindow.MONTHLY -> ProductRankMonthly.yearMonthToString(YearMonth.now(ZONE))
        }

        // 랭킹 조회
        val (rankings, totalCount) = rankingService.getTopN(window, timestamp, page, size)

        // 상품 정보 조회
        val productIds = rankings.map { it.productId }
        val productMap = rankingService.findProductsByIds(productIds)

        // 랭킹 아이템 생성
        val rankingItems = rankings.mapNotNull { ranking ->
            val product = productMap[ranking.productId] ?: return@mapNotNull null
            RankingItemInfo.from(ranking, product)
        }

        return RankingPageInfo(
            rankings = rankingItems,
            window = window,
            timestamp = timestamp,
            page = page,
            size = size,
            totalCount = totalCount,
        )
    }
}
