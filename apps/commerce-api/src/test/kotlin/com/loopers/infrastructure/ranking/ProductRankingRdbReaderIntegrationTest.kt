package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQuery
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@DisplayName("ProductRankingRdbReader 통합 테스트")
class ProductRankingRdbReaderIntegrationTest @Autowired constructor(
    private val productRankingRdbReader: ProductRankingRdbReader,
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    // KST 2025-01-15 14:00:00 = UTC 2025-01-15 05:00:00
    private val testDateTime: Instant = Instant.parse("2025-01-15T05:00:00Z")
    private val testBaseDate: LocalDate = LocalDate.of(2025, 1, 15)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("findTopRankings() - WEEKLY")
    @Nested
    inner class FindTopRankingsWeekly {

        @DisplayName("WEEKLY 기간으로 상위 N개의 랭킹을 조회한다")
        @Test
        fun `returns top N rankings for WEEKLY period`() {
            // given
            saveWeeklyRanking(testBaseDate, 1, 101L, BigDecimal("300.00"))
            saveWeeklyRanking(testBaseDate, 2, 102L, BigDecimal("200.00"))
            saveWeeklyRanking(testBaseDate, 3, 103L, BigDecimal("100.00"))

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 3,
            )

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then - limit + 1 = 4개까지 조회 시도하나 데이터가 3개뿐
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(101L)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[0].score).isEqualByComparingTo(BigDecimal("300.00"))

            assertThat(result[1].productId).isEqualTo(102L)
            assertThat(result[1].rank).isEqualTo(2)
            assertThat(result[1].score).isEqualByComparingTo(BigDecimal("200.00"))

            assertThat(result[2].productId).isEqualTo(103L)
            assertThat(result[2].rank).isEqualTo(3)
            assertThat(result[2].score).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("limit + 1개까지 조회한다 (hasNext 판단용)")
        @Test
        fun `returns limit plus one items for hasNext check`() {
            // given
            for (i in 1..10) {
                saveWeeklyRanking(testBaseDate, i, (100 + i).toLong(), BigDecimal((100 - i).toString()))
            }

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 3,
            )

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then - limit + 1 = 4
            assertThat(result).hasSize(4)
        }

        @DisplayName("데이터가 존재하지 않으면 빈 리스트를 반환한다")
        @Test
        fun `returns empty list when no data exists`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("offset을 사용하여 페이지네이션 조회한다")
        @Test
        fun `returns paginated rankings with offset`() {
            // given
            for (i in 1..5) {
                saveWeeklyRanking(testBaseDate, i, (100 + i).toLong(), BigDecimal((600 - i * 100).toString()))
            }

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 2,
                limit = 2,
            )

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then - offset 2부터 limit + 1 = 3개 조회
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(103L)
            assertThat(result[0].rank).isEqualTo(3)
            assertThat(result[1].productId).isEqualTo(104L)
            assertThat(result[1].rank).isEqualTo(4)
            assertThat(result[2].productId).isEqualTo(105L)
            assertThat(result[2].rank).isEqualTo(5)
        }
    }

    @DisplayName("findTopRankings() - MONTHLY")
    @Nested
    inner class FindTopRankingsMonthly {

        @DisplayName("MONTHLY 기간으로 상위 N개의 랭킹을 조회한다")
        @Test
        fun `returns top N rankings for MONTHLY period`() {
            // given
            saveMonthlyRanking(testBaseDate, 1, 201L, BigDecimal("500.00"))
            saveMonthlyRanking(testBaseDate, 2, 202L, BigDecimal("400.00"))
            saveMonthlyRanking(testBaseDate, 3, 203L, BigDecimal("300.00"))

            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 3,
            )

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(201L)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[0].score).isEqualByComparingTo(BigDecimal("500.00"))
        }

        @DisplayName("데이터가 존재하지 않으면 빈 리스트를 반환한다")
        @Test
        fun `returns empty list when no data exists for MONTHLY`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then
            assertThat(result).isEmpty()
        }
    }

    @DisplayName("findTopRankings() - unsupported period")
    @Nested
    inner class FindTopRankingsUnsupported {

        @DisplayName("HOURLY 기간은 지원하지 않아 예외를 발생시킨다")
        @Test
        fun `throws exception for HOURLY period`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when & then
            assertThrows<IllegalArgumentException> {
                productRankingRdbReader.findTopRankings(query)
            }
        }

        @DisplayName("DAILY 기간은 지원하지 않아 예외를 발생시킨다")
        @Test
        fun `throws exception for DAILY period`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when & then
            assertThrows<IllegalArgumentException> {
                productRankingRdbReader.findTopRankings(query)
            }
        }
    }

    @DisplayName("findRankByProductId() - WEEKLY")
    @Nested
    inner class FindRankByProductIdWeekly {

        @DisplayName("WEEKLY 기간에서 특정 상품의 순위를 반환한다")
        @Test
        fun `returns rank for specific product in WEEKLY period`() {
            // given
            saveWeeklyRanking(testBaseDate, 1, 101L, BigDecimal("300.00"))
            saveWeeklyRanking(testBaseDate, 2, 102L, BigDecimal("200.00"))
            saveWeeklyRanking(testBaseDate, 3, 103L, BigDecimal("100.00"))

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingRdbReader.findRankByProductId(query, 102L)

            // then
            assertThat(rank).isEqualTo(2)
        }

        @DisplayName("랭킹에 없는 상품의 순위는 null을 반환한다")
        @Test
        fun `returns null for non-existent product`() {
            // given
            saveWeeklyRanking(testBaseDate, 1, 101L, BigDecimal("300.00"))

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingRdbReader.findRankByProductId(query, 999L)

            // then
            assertThat(rank).isNull()
        }

        @DisplayName("데이터가 존재하지 않으면 null을 반환한다")
        @Test
        fun `returns null when no data exists`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingRdbReader.findRankByProductId(query, 101L)

            // then
            assertThat(rank).isNull()
        }
    }

    @DisplayName("findRankByProductId() - MONTHLY")
    @Nested
    inner class FindRankByProductIdMonthly {

        @DisplayName("MONTHLY 기간에서 특정 상품의 순위를 반환한다")
        @Test
        fun `returns rank for specific product in MONTHLY period`() {
            // given
            saveMonthlyRanking(testBaseDate, 1, 201L, BigDecimal("500.00"))
            saveMonthlyRanking(testBaseDate, 2, 202L, BigDecimal("400.00"))

            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingRdbReader.findRankByProductId(query, 202L)

            // then
            assertThat(rank).isEqualTo(2)
        }
    }

    @DisplayName("exists() - WEEKLY")
    @Nested
    inner class ExistsWeekly {

        @DisplayName("WEEKLY 데이터가 존재하면 true를 반환한다")
        @Test
        fun `returns true when WEEKLY data exists`() {
            // given
            saveWeeklyRanking(testBaseDate, 1, 101L, BigDecimal("300.00"))

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingRdbReader.exists(query)

            // then
            assertThat(result).isTrue()
        }

        @DisplayName("WEEKLY 데이터가 존재하지 않으면 false를 반환한다")
        @Test
        fun `returns false when WEEKLY data does not exist`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingRdbReader.exists(query)

            // then
            assertThat(result).isFalse()
        }
    }

    @DisplayName("exists() - MONTHLY")
    @Nested
    inner class ExistsMonthly {

        @DisplayName("MONTHLY 데이터가 존재하면 true를 반환한다")
        @Test
        fun `returns true when MONTHLY data exists`() {
            // given
            saveMonthlyRanking(testBaseDate, 1, 201L, BigDecimal("500.00"))

            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingRdbReader.exists(query)

            // then
            assertThat(result).isTrue()
        }

        @DisplayName("MONTHLY 데이터가 존재하지 않으면 false를 반환한다")
        @Test
        fun `returns false when MONTHLY data does not exist`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingRdbReader.exists(query)

            // then
            assertThat(result).isFalse()
        }
    }

    private fun saveWeeklyRanking(
        baseDate: LocalDate,
        rank: Int,
        productId: Long,
        score: BigDecimal,
    ): MvProductRankWeekly {
        return weeklyJpaRepository.save(
            MvProductRankWeekly(
                baseDate = baseDate,
                rank = rank,
                productId = productId,
                score = score,
            ),
        )
    }

    private fun saveMonthlyRanking(
        baseDate: LocalDate,
        rank: Int,
        productId: Long,
        score: BigDecimal,
    ): MvProductRankMonthly {
        return monthlyJpaRepository.save(
            MvProductRankMonthly(
                baseDate = baseDate,
                rank = rank,
                productId = productId,
                score = score,
            ),
        )
    }
}
