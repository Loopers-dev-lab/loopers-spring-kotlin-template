package com.loopers.support

import com.loopers.domain.product.ProductMetrics
import com.loopers.domain.ranking.ProductRankDaily
import com.loopers.domain.ranking.ProductRankMonthly
import com.loopers.domain.ranking.ProductRankWeekly
import com.loopers.domain.ranking.Ranking
import com.loopers.domain.ranking.RankingScore
import java.time.LocalDate
import java.time.YearMonth

/**
 * 배치 테스트 데이터 생성 팩토리
 */
object BatchTestDataFactory {

    /**
     * ProductRankDaily 생성
     */
    fun createProductRankDaily(
        rankingDate: LocalDate = LocalDate.now(),
        productId: Long = 1L,
        score: Double = 100.0,
        rank: Int = 1,
        likeCount: Long = 10,
        viewCount: Long = 100,
        salesCount: Long = 5,
    ): ProductRankDaily = ProductRankDaily(
            rankingDate = rankingDate,
            productId = productId,
            score = score,
            rank = rank,
            likeCount = likeCount,
            viewCount = viewCount,
            salesCount = salesCount,
        )

    /**
     * ProductMetrics 생성
     */
    fun createProductMetrics(
        productId: Long = 1L,
        likeCount: Long = 10,
        viewCount: Long = 100,
        salesCount: Long = 5,
        totalSalesAmount: Long = 500000,
    ): ProductMetrics = ProductMetrics(
            productId = productId,
            likeCount = likeCount,
            viewCount = viewCount,
            salesCount = salesCount,
            totalSalesAmount = totalSalesAmount,
        )

    /**
     * Ranking 생성
     */
    fun createRanking(
        productId: Long = 1L,
        score: Double = 100.0,
        rank: Int = 1,
    ): Ranking = Ranking(
            productId = productId,
            score = RankingScore(score),
            rank = rank,
        )

    /**
     * ProductRankWeekly 생성
     */
    fun createProductRankWeekly(
        yearWeek: String = "2025W03",
        productId: Long = 1L,
        score: Double = 100.0,
        rank: Int = 1,
        periodStart: LocalDate = LocalDate.of(2025, 1, 13),
        periodEnd: LocalDate = LocalDate.of(2025, 1, 19),
    ): ProductRankWeekly = ProductRankWeekly(
            yearWeek = yearWeek,
            productId = productId,
            score = score,
            rank = rank,
            periodStart = periodStart,
            periodEnd = periodEnd,
        )

    /**
     * ProductRankMonthly 생성
     */
    fun createProductRankMonthly(
        yearMonth: String = "202501",
        productId: Long = 1L,
        score: Double = 100.0,
        rank: Int = 1,
        periodStart: LocalDate = LocalDate.of(2025, 1, 1),
        periodEnd: LocalDate = LocalDate.of(2025, 1, 31),
    ): ProductRankMonthly = ProductRankMonthly(
            yearMonth = yearMonth,
            productId = productId,
            score = score,
            rank = rank,
            periodStart = periodStart,
            periodEnd = periodEnd,
        )

    /**
     * 주간 랭킹 데이터 생성 (7일치)
     * @param count 생성할 상품 개수
     * @param startDate 시작 날짜 (기본: 6일 전)
     */
    fun createWeeklyRankings(
        count: Int = 7,
        startDate: LocalDate = LocalDate.now().minusDays(6),
    ): List<ProductRankDaily> {
        val rankings = mutableListOf<ProductRankDaily>()

        for (dayOffset in 0..6) {
            val date = startDate.plusDays(dayOffset.toLong())
            for (productIndex in 1..count) {
                rankings.add(
                    createProductRankDaily(
                        rankingDate = date,
                        productId = productIndex.toLong(),
                        score = 100.0 - productIndex,
                        rank = productIndex,
                    ),
                )
            }
        }

        return rankings
    }

    /**
     * 월간 랭킹 데이터 생성 (한 달치)
     * @param count 생성할 상품 개수
     * @param yearMonth 년월
     */
    fun createMonthlyRankings(
        count: Int = 30,
        yearMonth: YearMonth = YearMonth.now(),
    ): List<ProductRankDaily> {
        val rankings = mutableListOf<ProductRankDaily>()
        val daysInMonth = yearMonth.lengthOfMonth()

        for (day in 1..daysInMonth) {
            val date = yearMonth.atDay(day)
            for (productIndex in 1..count) {
                rankings.add(
                    createProductRankDaily(
                        rankingDate = date,
                        productId = productIndex.toLong(),
                        score = 100.0 - productIndex,
                        rank = productIndex,
                    ),
                )
            }
        }

        return rankings
    }

    /**
     * 다양한 점수를 가진 일간 랭킹 리스트 생성
     * @param date 랭킹 날짜
     * @param productCount 생성할 상품 개수
     */
    fun createDailyRankingsWithVariedScores(
        date: LocalDate = LocalDate.now(),
        productCount: Int = 100,
    ): List<ProductRankDaily> = (1..productCount).map { index ->
            createProductRankDaily(
                rankingDate = date,
                productId = index.toLong(),
                score = 1000.0 - (index * 5.0), // 점수 차등
                rank = index,
                likeCount = (100 - index).toLong(),
                viewCount = (1000 - index * 5).toLong(),
                salesCount = (50 - index / 2).toLong(),
            )
        }
}
