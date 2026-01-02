package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQuery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@DisplayName("ProductRankingProxyReader 단위 테스트")
class ProductRankingProxyReaderTest {

    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var zSetOps: ZSetOperations<String, String>
    private lateinit var weeklyJpaRepository: MvProductRankWeeklyJpaRepository
    private lateinit var monthlyJpaRepository: MvProductRankMonthlyJpaRepository
    private lateinit var rankingKeyGenerator: RankingKeyGenerator
    private lateinit var proxyReader: ProductRankingProxyReader

    // KST 2025-01-15 14:00:00 = UTC 2025-01-15 05:00:00
    private val testDateTime: Instant = Instant.parse("2025-01-15T05:00:00Z")
    private val testBaseDate: LocalDate = LocalDate.of(2025, 1, 15)

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk()
        zSetOps = mockk()
        weeklyJpaRepository = mockk()
        monthlyJpaRepository = mockk()
        rankingKeyGenerator = mockk()

        every { redisTemplate.opsForZSet() } returns zSetOps

        proxyReader = ProductRankingProxyReader(
            redisTemplate,
            weeklyJpaRepository,
            monthlyJpaRepository,
            rankingKeyGenerator,
        )
    }

    @DisplayName("findTopRankings() - 라우팅 로직")
    @Nested
    inner class FindTopRankingsRouting {

        @DisplayName("HOURLY 기간은 Redis에서 조회한다")
        @Test
        fun `routes HOURLY period to Redis`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val bucketKey = "ranking:products:hourly:2025011514"
            every { rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime) } returns bucketKey
            every { zSetOps.reverseRangeWithScores(bucketKey, 0, 10) } returns setOf(
                mockTypedTuple("101", 100.0),
            )

            // when
            val result = proxyReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(101L)
            verify(exactly = 1) { zSetOps.reverseRangeWithScores(bucketKey, 0, 10) }
            verify(exactly = 0) { weeklyJpaRepository.findByBaseDateOrderByRankAsc(any(), any()) }
            verify(exactly = 0) { monthlyJpaRepository.findByBaseDateOrderByRankAsc(any(), any()) }
        }

        @DisplayName("DAILY 기간은 Redis에서 조회한다")
        @Test
        fun `routes DAILY period to Redis`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val bucketKey = "ranking:products:daily:20250115"
            every { rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, testDateTime) } returns bucketKey
            every { zSetOps.reverseRangeWithScores(bucketKey, 0, 10) } returns setOf(
                mockTypedTuple("102", 200.0),
            )

            // when
            val result = proxyReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(102L)
            verify(exactly = 1) { zSetOps.reverseRangeWithScores(bucketKey, 0, 10) }
        }

        @DisplayName("WEEKLY 기간은 RDB에서 조회한다")
        @Test
        fun `routes WEEKLY period to RDB`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val pageable = PageRequest.of(0, 11) // offset 0 + limit + 1 = 11
            every {
                weeklyJpaRepository.findByBaseDateOrderByRankAsc(testBaseDate, pageable)
            } returns listOf(
                MvProductRankWeekly(
                    baseDate = testBaseDate,
                    rank = 1,
                    productId = 103L,
                    score = BigDecimal("300.00"),
                ),
            )

            // when
            val result = proxyReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(103L)
            assertThat(result[0].rank).isEqualTo(1)
            verify(exactly = 1) { weeklyJpaRepository.findByBaseDateOrderByRankAsc(testBaseDate, pageable) }
            verify(exactly = 0) { zSetOps.reverseRangeWithScores(any(), any(), any()) }
        }

        @DisplayName("MONTHLY 기간은 RDB에서 조회한다")
        @Test
        fun `routes MONTHLY period to RDB`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val pageable = PageRequest.of(0, 11)
            every {
                monthlyJpaRepository.findByBaseDateOrderByRankAsc(testBaseDate, pageable)
            } returns listOf(
                MvProductRankMonthly(
                    baseDate = testBaseDate,
                    rank = 1,
                    productId = 104L,
                    score = BigDecimal("400.00"),
                ),
            )

            // when
            val result = proxyReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(104L)
            verify(exactly = 1) { monthlyJpaRepository.findByBaseDateOrderByRankAsc(testBaseDate, pageable) }
        }
    }

    @DisplayName("findRankByProductId() - 라우팅 로직")
    @Nested
    inner class FindRankByProductIdRouting {

        @DisplayName("HOURLY 기간은 Redis에서 순위를 조회한다")
        @Test
        fun `routes HOURLY period to Redis`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 101L
            val bucketKey = "ranking:products:hourly:2025011514"
            every { rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime) } returns bucketKey
            every { zSetOps.reverseRank(bucketKey, productId.toString()) } returns 4L // 0-indexed

            // when
            val result = proxyReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isEqualTo(5) // 1-indexed
            verify(exactly = 1) { zSetOps.reverseRank(bucketKey, productId.toString()) }
        }

        @DisplayName("DAILY 기간은 Redis에서 순위를 조회한다")
        @Test
        fun `routes DAILY period to Redis`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 102L
            val bucketKey = "ranking:products:daily:20250115"
            every { rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, testDateTime) } returns bucketKey
            every { zSetOps.reverseRank(bucketKey, productId.toString()) } returns 2L

            // when
            val result = proxyReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isEqualTo(3)
        }

        @DisplayName("WEEKLY 기간은 RDB에서 순위를 조회한다")
        @Test
        fun `routes WEEKLY period to RDB`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 103L
            every {
                weeklyJpaRepository.findByBaseDateAndProductId(testBaseDate, productId)
            } returns MvProductRankWeekly(
                baseDate = testBaseDate,
                rank = 1,
                productId = productId,
                score = BigDecimal("300.00"),
            )

            // when
            val result = proxyReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isEqualTo(1)
            verify(exactly = 1) { weeklyJpaRepository.findByBaseDateAndProductId(testBaseDate, productId) }
        }

        @DisplayName("MONTHLY 기간은 RDB에서 순위를 조회한다")
        @Test
        fun `routes MONTHLY period to RDB`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 104L
            every {
                monthlyJpaRepository.findByBaseDateAndProductId(testBaseDate, productId)
            } returns MvProductRankMonthly(
                baseDate = testBaseDate,
                rank = 2,
                productId = productId,
                score = BigDecimal("400.00"),
            )

            // when
            val result = proxyReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isEqualTo(2)
        }

        @DisplayName("Redis에서 찾지 못하면 null을 반환한다")
        @Test
        fun `returns null when not found in Redis`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 999L
            val bucketKey = "ranking:products:hourly:2025011514"
            every { rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime) } returns bucketKey
            every { zSetOps.reverseRank(bucketKey, productId.toString()) } returns null

            // when
            val result = proxyReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isNull()
        }

        @DisplayName("RDB에서 찾지 못하면 null을 반환한다")
        @Test
        fun `returns null when not found in RDB`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 999L
            every {
                weeklyJpaRepository.findByBaseDateAndProductId(testBaseDate, productId)
            } returns null

            // when
            val result = proxyReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isNull()
        }
    }

    @DisplayName("exists() - 라우팅 로직")
    @Nested
    inner class ExistsRouting {

        @DisplayName("HOURLY 기간은 Redis에서 존재 여부를 확인한다")
        @Test
        fun `routes HOURLY period to Redis`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val bucketKey = "ranking:products:hourly:2025011514"
            every { rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime) } returns bucketKey
            every { redisTemplate.hasKey(bucketKey) } returns true

            // when
            val result = proxyReader.exists(query)

            // then
            assertThat(result).isTrue()
            verify(exactly = 1) { redisTemplate.hasKey(bucketKey) }
        }

        @DisplayName("DAILY 기간은 Redis에서 존재 여부를 확인한다")
        @Test
        fun `routes DAILY period to Redis`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val bucketKey = "ranking:products:daily:20250115"
            every { rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, testDateTime) } returns bucketKey
            every { redisTemplate.hasKey(bucketKey) } returns false

            // when
            val result = proxyReader.exists(query)

            // then
            assertThat(result).isFalse()
        }

        @DisplayName("WEEKLY 기간은 RDB에서 존재 여부를 확인한다")
        @Test
        fun `routes WEEKLY period to RDB`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            every { weeklyJpaRepository.existsByBaseDate(testBaseDate) } returns true

            // when
            val result = proxyReader.exists(query)

            // then
            assertThat(result).isTrue()
            verify(exactly = 1) { weeklyJpaRepository.existsByBaseDate(testBaseDate) }
        }

        @DisplayName("MONTHLY 기간은 RDB에서 존재 여부를 확인한다")
        @Test
        fun `routes MONTHLY period to RDB`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            every { monthlyJpaRepository.existsByBaseDate(testBaseDate) } returns false

            // when
            val result = proxyReader.exists(query)

            // then
            assertThat(result).isFalse()
            verify(exactly = 1) { monthlyJpaRepository.existsByBaseDate(testBaseDate) }
        }
    }

    @DisplayName("LSP 준수 - 모든 RankingPeriod 값 처리")
    @Nested
    inner class LspCompliance {

        @DisplayName("모든 RankingPeriod 값에 대해 findTopRankings가 예외 없이 동작한다")
        @Test
        fun `findTopRankings handles all RankingPeriod values without exception`() {
            // given
            val hourlyBucketKey = "ranking:products:hourly:2025011514"
            val dailyBucketKey = "ranking:products:daily:20250115"

            every { rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime) } returns hourlyBucketKey
            every { rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, testDateTime) } returns dailyBucketKey
            every { zSetOps.reverseRangeWithScores(any(), any(), any()) } returns emptySet()
            every { weeklyJpaRepository.findByBaseDateOrderByRankAsc(any(), any()) } returns emptyList()
            every { monthlyJpaRepository.findByBaseDateOrderByRankAsc(any(), any()) } returns emptyList()

            // when & then - no exception thrown
            RankingPeriod.entries.forEach { period ->
                val query = RankingQuery(
                    period = period,
                    dateTime = testDateTime,
                    offset = 0,
                    limit = 10,
                )
                proxyReader.findTopRankings(query)
            }
        }

        @DisplayName("모든 RankingPeriod 값에 대해 findRankByProductId가 예외 없이 동작한다")
        @Test
        fun `findRankByProductId handles all RankingPeriod values without exception`() {
            // given
            val hourlyBucketKey = "ranking:products:hourly:2025011514"
            val dailyBucketKey = "ranking:products:daily:20250115"

            every { rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime) } returns hourlyBucketKey
            every { rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, testDateTime) } returns dailyBucketKey
            every { zSetOps.reverseRank(any(), any()) } returns null
            every { weeklyJpaRepository.findByBaseDateAndProductId(any(), any()) } returns null
            every { monthlyJpaRepository.findByBaseDateAndProductId(any(), any()) } returns null

            // when & then - no exception thrown
            RankingPeriod.entries.forEach { period ->
                val query = RankingQuery(
                    period = period,
                    dateTime = testDateTime,
                    offset = 0,
                    limit = 10,
                )
                proxyReader.findRankByProductId(query, 101L)
            }
        }

        @DisplayName("모든 RankingPeriod 값에 대해 exists가 예외 없이 동작한다")
        @Test
        fun `exists handles all RankingPeriod values without exception`() {
            // given
            val hourlyBucketKey = "ranking:products:hourly:2025011514"
            val dailyBucketKey = "ranking:products:daily:20250115"

            every { rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime) } returns hourlyBucketKey
            every { rankingKeyGenerator.bucketKey(RankingPeriod.DAILY, testDateTime) } returns dailyBucketKey
            every { redisTemplate.hasKey(any()) } returns false
            every { weeklyJpaRepository.existsByBaseDate(any()) } returns false
            every { monthlyJpaRepository.existsByBaseDate(any()) } returns false

            // when & then - no exception thrown
            RankingPeriod.entries.forEach { period ->
                val query = RankingQuery(
                    period = period,
                    dateTime = testDateTime,
                    offset = 0,
                    limit = 10,
                )
                proxyReader.exists(query)
            }
        }
    }

    private fun mockTypedTuple(value: String, score: Double): ZSetOperations.TypedTuple<String> {
        val tuple = mockk<ZSetOperations.TypedTuple<String>>()
        every { tuple.value } returns value
        every { tuple.score } returns score
        return tuple
    }
}
