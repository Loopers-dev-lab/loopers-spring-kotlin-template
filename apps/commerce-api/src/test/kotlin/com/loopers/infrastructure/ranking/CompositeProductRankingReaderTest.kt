package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRanking
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
import java.math.BigDecimal
import java.time.Instant

@DisplayName("CompositeProductRankingReader 단위 테스트")
class CompositeProductRankingReaderTest {

    private lateinit var redisReader: ProductRankingRedisReader
    private lateinit var rdbReader: ProductRankingRdbReader
    private lateinit var compositeReader: CompositeProductRankingReader

    // KST 2025-01-15 14:00:00 = UTC 2025-01-15 05:00:00
    private val testDateTime: Instant = Instant.parse("2025-01-15T05:00:00Z")

    @BeforeEach
    fun setUp() {
        redisReader = mockk()
        rdbReader = mockk()
        compositeReader = CompositeProductRankingReader(redisReader, rdbReader)
    }

    @DisplayName("findTopRankings()")
    @Nested
    inner class FindTopRankings {

        @DisplayName("HOURLY 기간은 Redis Reader로 위임한다")
        @Test
        fun `delegates HOURLY period to Redis Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val expectedRankings = listOf(
                ProductRanking(productId = 101L, rank = 1, score = BigDecimal("100.0")),
            )
            every { redisReader.findTopRankings(query) } returns expectedRankings

            // when
            val result = compositeReader.findTopRankings(query)

            // then
            assertThat(result).isEqualTo(expectedRankings)
            verify(exactly = 1) { redisReader.findTopRankings(query) }
            verify(exactly = 0) { rdbReader.findTopRankings(any()) }
        }

        @DisplayName("DAILY 기간은 Redis Reader로 위임한다")
        @Test
        fun `delegates DAILY period to Redis Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val expectedRankings = listOf(
                ProductRanking(productId = 102L, rank = 1, score = BigDecimal("200.0")),
            )
            every { redisReader.findTopRankings(query) } returns expectedRankings

            // when
            val result = compositeReader.findTopRankings(query)

            // then
            assertThat(result).isEqualTo(expectedRankings)
            verify(exactly = 1) { redisReader.findTopRankings(query) }
            verify(exactly = 0) { rdbReader.findTopRankings(any()) }
        }

        @DisplayName("WEEKLY 기간은 RDB Reader로 위임한다")
        @Test
        fun `delegates WEEKLY period to RDB Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val expectedRankings = listOf(
                ProductRanking(productId = 103L, rank = 1, score = BigDecimal("300.0")),
            )
            every { rdbReader.findTopRankings(query) } returns expectedRankings

            // when
            val result = compositeReader.findTopRankings(query)

            // then
            assertThat(result).isEqualTo(expectedRankings)
            verify(exactly = 1) { rdbReader.findTopRankings(query) }
            verify(exactly = 0) { redisReader.findTopRankings(any()) }
        }

        @DisplayName("MONTHLY 기간은 RDB Reader로 위임한다")
        @Test
        fun `delegates MONTHLY period to RDB Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val expectedRankings = listOf(
                ProductRanking(productId = 104L, rank = 1, score = BigDecimal("400.0")),
            )
            every { rdbReader.findTopRankings(query) } returns expectedRankings

            // when
            val result = compositeReader.findTopRankings(query)

            // then
            assertThat(result).isEqualTo(expectedRankings)
            verify(exactly = 1) { rdbReader.findTopRankings(query) }
            verify(exactly = 0) { redisReader.findTopRankings(any()) }
        }
    }

    @DisplayName("findRankByProductId()")
    @Nested
    inner class FindRankByProductId {

        @DisplayName("HOURLY 기간은 Redis Reader로 위임한다")
        @Test
        fun `delegates HOURLY period to Redis Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 101L
            every { redisReader.findRankByProductId(query, productId) } returns 5

            // when
            val result = compositeReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isEqualTo(5)
            verify(exactly = 1) { redisReader.findRankByProductId(query, productId) }
            verify(exactly = 0) { rdbReader.findRankByProductId(any(), any()) }
        }

        @DisplayName("DAILY 기간은 Redis Reader로 위임한다")
        @Test
        fun `delegates DAILY period to Redis Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 102L
            every { redisReader.findRankByProductId(query, productId) } returns 3

            // when
            val result = compositeReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isEqualTo(3)
            verify(exactly = 1) { redisReader.findRankByProductId(query, productId) }
            verify(exactly = 0) { rdbReader.findRankByProductId(any(), any()) }
        }

        @DisplayName("WEEKLY 기간은 RDB Reader로 위임한다")
        @Test
        fun `delegates WEEKLY period to RDB Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 103L
            every { rdbReader.findRankByProductId(query, productId) } returns 1

            // when
            val result = compositeReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isEqualTo(1)
            verify(exactly = 1) { rdbReader.findRankByProductId(query, productId) }
            verify(exactly = 0) { redisReader.findRankByProductId(any(), any()) }
        }

        @DisplayName("MONTHLY 기간은 RDB Reader로 위임한다")
        @Test
        fun `delegates MONTHLY period to RDB Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            val productId = 104L
            every { rdbReader.findRankByProductId(query, productId) } returns 2

            // when
            val result = compositeReader.findRankByProductId(query, productId)

            // then
            assertThat(result).isEqualTo(2)
            verify(exactly = 1) { rdbReader.findRankByProductId(query, productId) }
            verify(exactly = 0) { redisReader.findRankByProductId(any(), any()) }
        }
    }

    @DisplayName("exists()")
    @Nested
    inner class Exists {

        @DisplayName("HOURLY 기간은 Redis Reader로 위임한다")
        @Test
        fun `delegates HOURLY period to Redis Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            every { redisReader.exists(query) } returns true

            // when
            val result = compositeReader.exists(query)

            // then
            assertThat(result).isTrue()
            verify(exactly = 1) { redisReader.exists(query) }
            verify(exactly = 0) { rdbReader.exists(any()) }
        }

        @DisplayName("DAILY 기간은 Redis Reader로 위임한다")
        @Test
        fun `delegates DAILY period to Redis Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            every { redisReader.exists(query) } returns false

            // when
            val result = compositeReader.exists(query)

            // then
            assertThat(result).isFalse()
            verify(exactly = 1) { redisReader.exists(query) }
            verify(exactly = 0) { rdbReader.exists(any()) }
        }

        @DisplayName("WEEKLY 기간은 RDB Reader로 위임한다")
        @Test
        fun `delegates WEEKLY period to RDB Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            every { rdbReader.exists(query) } returns true

            // when
            val result = compositeReader.exists(query)

            // then
            assertThat(result).isTrue()
            verify(exactly = 1) { rdbReader.exists(query) }
            verify(exactly = 0) { redisReader.exists(any()) }
        }

        @DisplayName("MONTHLY 기간은 RDB Reader로 위임한다")
        @Test
        fun `delegates MONTHLY period to RDB Reader`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )
            every { rdbReader.exists(query) } returns false

            // when
            val result = compositeReader.exists(query)

            // then
            assertThat(result).isFalse()
            verify(exactly = 1) { rdbReader.exists(query) }
            verify(exactly = 0) { redisReader.exists(any()) }
        }
    }
}
