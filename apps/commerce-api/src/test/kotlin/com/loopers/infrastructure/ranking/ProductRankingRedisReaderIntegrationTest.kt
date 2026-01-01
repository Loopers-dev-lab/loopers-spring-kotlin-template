package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.ProductRankingReader
import com.loopers.domain.ranking.RankingKeyGenerator
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQuery
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

@SpringBootTest
@DisplayName("ProductRankingRedisReader 통합 테스트")
class ProductRankingRedisReaderIntegrationTest @Autowired constructor(
    private val productRankingReader: ProductRankingReader,
    private val rankingKeyGenerator: RankingKeyGenerator,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {

    private val zSetOps = redisTemplate.opsForZSet()

    // KST 2025-01-15 14:00:00 = UTC 2025-01-15 05:00:00
    private val testDateTime: Instant = Instant.parse("2025-01-15T05:00:00Z")

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("findTopRankings()")
    @Nested
    inner class FindTopRankings {

        @DisplayName("RankingQuery를 사용하여 상위 N개의 랭킹을 조회한다")
        @Test
        fun `returns top N rankings using RankingQuery`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            zSetOps.add(bucketKey, "101", 100.0)
            zSetOps.add(bucketKey, "102", 300.0)
            zSetOps.add(bucketKey, "103", 200.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 3,
            )

            // when
            val result = productRankingReader.findTopRankings(query)

            // then - limit + 1 = 4개까지 조회 시도하나 데이터가 3개뿐
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(102L)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[0].score).isEqualByComparingTo(BigDecimal("300.0"))

            assertThat(result[1].productId).isEqualTo(103L)
            assertThat(result[1].rank).isEqualTo(2)
            assertThat(result[1].score).isEqualByComparingTo(BigDecimal("200.0"))

            assertThat(result[2].productId).isEqualTo(101L)
            assertThat(result[2].rank).isEqualTo(3)
            assertThat(result[2].score).isEqualByComparingTo(BigDecimal("100.0"))
        }

        @DisplayName("limit + 1개까지 조회한다 (hasNext 판단용)")
        @Test
        fun `returns limit plus one items for hasNext check`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            for (i in 1..10) {
                zSetOps.add(bucketKey, i.toString(), (100 - i).toDouble())
            }

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 3,
            )

            // when
            val result = productRankingReader.findTopRankings(query)

            // then - limit + 1 = 4
            assertThat(result).hasSize(4)
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
            val result = productRankingReader.findTopRankings(query)

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("offset을 사용하여 페이지네이션 조회한다")
        @Test
        fun `returns paginated rankings with offset`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            zSetOps.add(bucketKey, "101", 500.0)
            zSetOps.add(bucketKey, "102", 400.0)
            zSetOps.add(bucketKey, "103", 300.0)
            zSetOps.add(bucketKey, "104", 200.0)
            zSetOps.add(bucketKey, "105", 100.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 2,
                limit = 2,
            )

            // when
            val result = productRankingReader.findTopRankings(query)

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

    @DisplayName("findRankByProductId()")
    @Nested
    inner class FindRankByProductId {

        @DisplayName("특정 상품의 순위를 1-based로 반환한다")
        @Test
        fun `returns 1-based rank for specific product`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            zSetOps.add(bucketKey, "101", 100.0)
            zSetOps.add(bucketKey, "102", 300.0)
            zSetOps.add(bucketKey, "103", 200.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingReader.findRankByProductId(query, 102L)

            // then
            assertThat(rank).isEqualTo(1)
        }

        @DisplayName("점수가 두 번째로 높은 상품의 순위는 2이다")
        @Test
        fun `returns rank 2 for second highest score product`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            zSetOps.add(bucketKey, "101", 100.0)
            zSetOps.add(bucketKey, "102", 300.0)
            zSetOps.add(bucketKey, "103", 200.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingReader.findRankByProductId(query, 103L)

            // then
            assertThat(rank).isEqualTo(2)
        }

        @DisplayName("랭킹에 없는 상품의 순위는 null을 반환한다")
        @Test
        fun `returns null for non-existent product`() {
            // given
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            zSetOps.add(bucketKey, "101", 100.0)
            zSetOps.add(bucketKey, "102", 200.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingReader.findRankByProductId(query, 999L)

            // then
            assertThat(rank).isNull()
        }

        @DisplayName("버킷 키가 존재하지 않으면 null을 반환한다")
        @Test
        fun `returns null when bucket key does not exist`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingReader.findRankByProductId(query, 101L)

            // then
            assertThat(rank).isNull()
        }
    }

    @DisplayName("exists()")
    @Nested
    inner class Exists {

        @DisplayName("버킷 키가 존재하면 true를 반환한다")
        @Test
        fun `returns true when bucket key exists`() {
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
            val result = productRankingReader.exists(query)

            // then
            assertThat(result).isTrue()
        }

        @DisplayName("버킷 키가 존재하지 않으면 false를 반환한다")
        @Test
        fun `returns false when bucket key does not exist`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingReader.exists(query)

            // then
            assertThat(result).isFalse()
        }
    }
}
