package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQuery
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@DisplayName("ProductRankingProxyReader 통합 테스트")
class ProductRankingProxyReaderIntegrationTest @Autowired constructor(
    private val productRankingProxyReader: ProductRankingProxyReader,
    private val rankingKeyGenerator: RankingKeyGenerator,
    private val redisTemplate: RedisTemplate<String, String>,
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    private val zSetOps = redisTemplate.opsForZSet()

    // KST 2025-01-15 14:00:00 = UTC 2025-01-15 05:00:00
    private val testDateTime: Instant = Instant.parse("2025-01-15T05:00:00Z")
    private val testBaseDate: LocalDate = LocalDate.of(2025, 1, 15)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("findTopRankings() - HOURLY (Redis)")
    @Nested
    inner class FindTopRankingsHourly {

        @DisplayName("HOURLY 랭킹을 Redis에서 조회한다")
        @Test
        fun `returns hourly rankings from Redis`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            zSetOps.add(bucketKey, "101", 300.0)
            zSetOps.add(bucketKey, "102", 200.0)
            zSetOps.add(bucketKey, "103", 100.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 3,
            )

            // when
            val result = productRankingProxyReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(101L)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[0].score).isEqualByComparingTo(BigDecimal("300.0"))
            assertThat(result[1].productId).isEqualTo(102L)
            assertThat(result[2].productId).isEqualTo(103L)
        }

        @DisplayName("버킷 키가 존재하지 않으면 빈 리스트를 반환한다")
        @Test
        fun `returns empty list when bucket key does not exist`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingProxyReader.findTopRankings(query)

            // then
            assertThat(result).isEmpty()
        }
    }

    @DisplayName("findTopRankings() - DAILY (Redis)")
    @Nested
    inner class FindTopRankingsDaily {

        @DisplayName("DAILY 랭킹을 Redis에서 조회한다")
        @Test
        fun `returns daily rankings from Redis`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, testDateTime)
            zSetOps.add(bucketKey, "201", 500.0)
            zSetOps.add(bucketKey, "202", 400.0)

            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingProxyReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(201L)
            assertThat(result[1].productId).isEqualTo(202L)
        }
    }

    @DisplayName("findTopRankings() - WEEKLY (RDB)")
    @Nested
    inner class FindTopRankingsWeekly {

        @DisplayName("WEEKLY 랭킹을 RDB에서 조회한다")
        @Test
        fun `returns weekly rankings from RDB`() {
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
            val result = productRankingProxyReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(101L)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[0].score).isEqualByComparingTo(BigDecimal("300.00"))
            assertThat(result[1].productId).isEqualTo(102L)
            assertThat(result[2].productId).isEqualTo(103L)
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
            val result = productRankingProxyReader.findTopRankings(query)

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
            val result = productRankingProxyReader.findTopRankings(query)

            // then - offset 2부터 limit + 1 = 3개 조회
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(103L)
            assertThat(result[0].rank).isEqualTo(3)
        }
    }

    @DisplayName("findTopRankings() - MONTHLY (RDB)")
    @Nested
    inner class FindTopRankingsMonthly {

        @DisplayName("MONTHLY 랭킹을 RDB에서 조회한다")
        @Test
        fun `returns monthly rankings from RDB`() {
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
            val result = productRankingProxyReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(201L)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[1].productId).isEqualTo(202L)
        }
    }

    @DisplayName("findRankByProductId() - 모든 기간")
    @Nested
    inner class FindRankByProductId {

        @DisplayName("HOURLY 기간에서 특정 상품의 순위를 반환한다")
        @Test
        fun `returns rank for HOURLY period`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            zSetOps.add(bucketKey, "101", 300.0)
            zSetOps.add(bucketKey, "102", 200.0)
            zSetOps.add(bucketKey, "103", 100.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingProxyReader.findRankByProductId(query, 102L)

            // then (102가 두 번째로 높은 점수)
            assertThat(rank).isEqualTo(2)
        }

        @DisplayName("WEEKLY 기간에서 특정 상품의 순위를 반환한다")
        @Test
        fun `returns rank for WEEKLY period`() {
            // given
            saveWeeklyRanking(testBaseDate, 1, 101L, BigDecimal("300.00"))
            saveWeeklyRanking(testBaseDate, 2, 102L, BigDecimal("200.00"))

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingProxyReader.findRankByProductId(query, 102L)

            // then
            assertThat(rank).isEqualTo(2)
        }

        @DisplayName("MONTHLY 기간에서 특정 상품의 순위를 반환한다")
        @Test
        fun `returns rank for MONTHLY period`() {
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
            val rank = productRankingProxyReader.findRankByProductId(query, 202L)

            // then
            assertThat(rank).isEqualTo(2)
        }

        @DisplayName("랭킹에 없는 상품의 순위는 null을 반환한다")
        @Test
        fun `returns null for non-existent product`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingProxyReader.findRankByProductId(query, 999L)

            // then
            assertThat(rank).isNull()
        }
    }

    @DisplayName("exists() - 모든 기간")
    @Nested
    inner class Exists {

        @DisplayName("HOURLY 버킷 키가 존재하면 true를 반환한다")
        @Test
        fun `returns true when HOURLY bucket key exists`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            zSetOps.add(bucketKey, "101", 100.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingProxyReader.exists(query)

            // then
            assertThat(result).isTrue()
        }

        @DisplayName("DAILY 버킷 키가 존재하지 않으면 false를 반환한다")
        @Test
        fun `returns false when DAILY bucket key does not exist`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingProxyReader.exists(query)

            // then
            assertThat(result).isFalse()
        }

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
            val result = productRankingProxyReader.exists(query)

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
            val result = productRankingProxyReader.exists(query)

            // then
            assertThat(result).isFalse()
        }
    }

    @DisplayName("LSP 준수 - 모든 RankingPeriod 예외 없이 처리")
    @Nested
    inner class LspCompliance {

        @DisplayName("모든 RankingPeriod 값에 대해 예외 없이 동작한다")
        @Test
        fun `handles all RankingPeriod values without exception`() {
            // given & when & then - no exception thrown
            RankingPeriod.entries.forEach { period ->
                val query = RankingQuery(
                    period = period,
                    dateTime = testDateTime,
                    offset = 0,
                    limit = 10,
                )

                // All methods should work without exception
                productRankingProxyReader.findTopRankings(query)
                productRankingProxyReader.findRankByProductId(query, 101L)
                productRankingProxyReader.exists(query)
            }
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
