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
import java.time.ZoneId
import java.time.ZonedDateTime

@SpringBootTest
@DisplayName("ProductRankingRedisReader 통합 테스트")
class ProductRankingRedisReaderIntegrationTest @Autowired constructor(
    private val productRankingReader: ProductRankingReader,
    private val rankingKeyGenerator: RankingKeyGenerator,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {

    private val zSetOps = redisTemplate.opsForZSet()
    private val seoulZone = ZoneId.of("Asia/Seoul")
    private val testDateTime = ZonedDateTime.of(2025, 1, 15, 14, 0, 0, 0, seoulZone)

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

        @DisplayName("현재 버킷에 데이터가 있으면 그대로 반환한다 (AC-3 current bucket case)")
        @Test
        fun `returns data from current bucket when exists`() {
            // given
            val currentBucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, testDateTime)
            val previousBucketKey = rankingKeyGenerator.bucketKey(
                RankingPeriod.HOURLY,
                testDateTime.minusHours(1),
            )
            zSetOps.add(currentBucketKey, "101", 300.0)
            zSetOps.add(previousBucketKey, "201", 500.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingReader.findTopRankings(query)

            // then - 현재 버킷의 데이터만 반환
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(101L)
        }

        @DisplayName("현재 버킷이 비어있고 offset=0이면 이전 버킷에서 조회한다 (AC-3)")
        @Test
        fun `falls back to previous bucket when current is empty and offset is 0`() {
            // given
            val previousBucketKey = rankingKeyGenerator.bucketKey(
                RankingPeriod.HOURLY,
                testDateTime.minusHours(1),
            )
            zSetOps.add(previousBucketKey, "201", 500.0)
            zSetOps.add(previousBucketKey, "202", 400.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(201L)
            assertThat(result[1].productId).isEqualTo(202L)
        }

        @DisplayName("offset > 0이면 fallback을 시도하지 않는다 (AC-5)")
        @Test
        fun `does not fallback when offset is greater than 0`() {
            // given
            val previousBucketKey = rankingKeyGenerator.bucketKey(
                RankingPeriod.HOURLY,
                testDateTime.minusHours(1),
            )
            zSetOps.add(previousBucketKey, "201", 500.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 10,
                limit = 10,
            )

            // when
            val result = productRankingReader.findTopRankings(query)

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("명시적인 date가 있어도 fallback을 적용한다 (AC-4)")
        @Test
        fun `fallback applies even with explicit date (AC-4)`() {
            // given - 특정 시간의 버킷은 비어있고, 이전 버킷에 데이터가 있음
            val explicitDateTime = ZonedDateTime.of(2025, 1, 15, 14, 0, 0, 0, seoulZone)
            val previousDateTime = explicitDateTime.minusHours(1)
            val previousBucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, previousDateTime)
            zSetOps.add(previousBucketKey, "201", 500.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = explicitDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingReader.findTopRankings(query)

            // then - fallback이 적용되어 이전 버킷의 데이터 반환
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(201L)
        }

        @DisplayName("fallback은 한 번만 시도한다 (AC-6)")
        @Test
        fun `fallback only tries once (AC-6 single-step limit)`() {
            // given - 현재와 바로 이전 버킷은 비어있고, 두 단계 이전 버킷에만 데이터가 있음
            val twoPeriodsBefore = rankingKeyGenerator.bucketKey(
                RankingPeriod.HOURLY,
                testDateTime.minusHours(2),
            )
            zSetOps.add(twoPeriodsBefore, "301", 600.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingReader.findTopRankings(query)

            // then - 두 단계 이전 버킷은 조회하지 않으므로 빈 결과
            assertThat(result).isEmpty()
        }

        @DisplayName("DAILY period에서 fallback은 이전 날짜 버킷으로 이동한다 (AC-7)")
        @Test
        fun `DAILY period fallback goes to previous day bucket (AC-7)`() {
            // given
            val dailyDateTime = ZonedDateTime.of(2025, 1, 15, 0, 0, 0, 0, seoulZone)
            val previousDayBucketKey = rankingKeyGenerator.bucketKey(
                RankingPeriod.DAILY,
                dailyDateTime.minusDays(1),
            )
            zSetOps.add(previousDayBucketKey, "201", 500.0)

            val query = RankingQuery(
                period = RankingPeriod.DAILY,
                dateTime = dailyDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(201L)
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
