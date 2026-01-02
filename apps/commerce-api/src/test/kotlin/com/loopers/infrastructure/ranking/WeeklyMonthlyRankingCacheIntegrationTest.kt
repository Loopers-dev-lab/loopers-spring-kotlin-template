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
import java.time.ZoneId

@SpringBootTest
@DisplayName("Weekly/Monthly 랭킹 Cache-Aside 통합 테스트")
class WeeklyMonthlyRankingCacheIntegrationTest @Autowired constructor(
    private val productRankingRdbReader: ProductRankingRdbReader,
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val rankingKeyGenerator: RankingKeyGenerator,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
        private const val CACHE_SUFFIX = ":cache"
    }

    // KST 2025-01-15 14:00:00 = UTC 2025-01-15 05:00:00
    private val testDateTime: Instant = Instant.parse("2025-01-15T05:00:00Z")
    private val testBaseDate: LocalDate = LocalDate.of(2025, 1, 15)

    private val zSetOps = redisTemplate.opsForZSet()

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("Cache-Aside 패턴 동작 - WEEKLY")
    @Nested
    inner class CacheAsideWeekly {

        @DisplayName("캐시가 없으면 RDB에서 조회 후 캐시에 저장한다")
        @Test
        fun `caches RDB result when cache miss`() {
            // given
            saveWeeklyRanking(testBaseDate, 1, 101L, BigDecimal("300.00"))
            saveWeeklyRanking(testBaseDate, 2, 102L, BigDecimal("200.00"))

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            val cacheKey = getCacheKey(RankingPeriod.WEEKLY, testDateTime)
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(2)
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue()

            // verify cache content
            val cachedSize = zSetOps.size(cacheKey)
            assertThat(cachedSize).isEqualTo(2)
        }

        @DisplayName("캐시가 있으면 RDB를 조회하지 않고 캐시에서 반환한다")
        @Test
        fun `returns from cache when cache hit`() {
            // given - 캐시에 직접 데이터 추가
            val cacheKey = getCacheKey(RankingPeriod.WEEKLY, testDateTime)
            zSetOps.add(cacheKey, "201", 500.0)
            zSetOps.add(cacheKey, "202", 400.0)

            // RDB에는 다른 데이터 추가 (캐시 히트 시 조회되지 않음)
            saveWeeklyRanking(testBaseDate, 1, 101L, BigDecimal("300.00"))

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then - 캐시 데이터가 반환됨 (RDB의 101이 아닌 캐시의 201, 202)
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(201L)
            assertThat(result[0].score).isEqualByComparingTo(BigDecimal("500"))
            assertThat(result[1].productId).isEqualTo(202L)
            assertThat(result[1].score).isEqualByComparingTo(BigDecimal("400"))
        }

        @DisplayName("빈 RDB 결과는 캐시하지 않는다")
        @Test
        fun `does not cache empty result`() {
            // given
            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            val cacheKey = getCacheKey(RankingPeriod.WEEKLY, testDateTime)

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then
            assertThat(result).isEmpty()
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()
        }

        @DisplayName("캐시에 TTL이 설정된다")
        @Test
        fun `cache has TTL set`() {
            // given
            saveWeeklyRanking(testBaseDate, 1, 101L, BigDecimal("300.00"))

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            val cacheKey = getCacheKey(RankingPeriod.WEEKLY, testDateTime)

            // when
            productRankingRdbReader.findTopRankings(query)

            // then
            val ttl = redisTemplate.getExpire(cacheKey)
            assertThat(ttl).isGreaterThan(0)
            assertThat(ttl).isLessThanOrEqualTo(3600) // 1시간 이하
        }
    }

    @DisplayName("Cache-Aside 패턴 동작 - MONTHLY")
    @Nested
    inner class CacheAsideMonthly {

        @DisplayName("캐시가 없으면 RDB에서 조회 후 캐시에 저장한다")
        @Test
        fun `caches RDB result when cache miss for MONTHLY`() {
            // given
            saveMonthlyRanking(testBaseDate, 1, 301L, BigDecimal("600.00"))
            saveMonthlyRanking(testBaseDate, 2, 302L, BigDecimal("500.00"))
            saveMonthlyRanking(testBaseDate, 3, 303L, BigDecimal("400.00"))

            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            val cacheKey = getCacheKey(RankingPeriod.MONTHLY, testDateTime)
            assertThat(redisTemplate.hasKey(cacheKey)).isFalse()

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(3)
            assertThat(redisTemplate.hasKey(cacheKey)).isTrue()

            val cachedSize = zSetOps.size(cacheKey)
            assertThat(cachedSize).isEqualTo(3)
        }

        @DisplayName("캐시가 있으면 캐시에서 반환한다")
        @Test
        fun `returns from cache when cache hit for MONTHLY`() {
            // given - 캐시에 직접 데이터 추가
            val cacheKey = getCacheKey(RankingPeriod.MONTHLY, testDateTime)
            zSetOps.add(cacheKey, "401", 700.0)

            val query = RankingQuery(
                period = RankingPeriod.MONTHLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val result = productRankingRdbReader.findTopRankings(query)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(401L)
        }
    }

    @DisplayName("exists() 캐시 동작")
    @Nested
    inner class ExistsWithCache {

        @DisplayName("캐시가 있으면 RDB 조회 없이 true 반환")
        @Test
        fun `returns true from cache without RDB query`() {
            // given - 캐시에만 데이터 추가 (RDB 없음)
            val cacheKey = getCacheKey(RankingPeriod.WEEKLY, testDateTime)
            zSetOps.add(cacheKey, "101", 100.0)

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

        @DisplayName("캐시가 없으면 RDB에서 확인")
        @Test
        fun `checks RDB when cache miss`() {
            // given - RDB에만 데이터
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
    }

    @DisplayName("findRankByProductId() 캐시 동작")
    @Nested
    inner class FindRankByProductIdWithCache {

        @DisplayName("캐시에서 특정 상품의 순위를 조회한다")
        @Test
        fun `returns rank from cache`() {
            // given
            val cacheKey = getCacheKey(RankingPeriod.WEEKLY, testDateTime)
            zSetOps.add(cacheKey, "101", 300.0)
            zSetOps.add(cacheKey, "102", 200.0)
            zSetOps.add(cacheKey, "103", 100.0)

            val query = RankingQuery(
                period = RankingPeriod.WEEKLY,
                dateTime = testDateTime,
                offset = 0,
                limit = 10,
            )

            // when
            val rank = productRankingRdbReader.findRankByProductId(query, 102L)

            // then (102가 두 번째로 높은 점수이므로 rank 2)
            assertThat(rank).isEqualTo(2)
        }

        @DisplayName("캐시 미스 시 RDB에서 순위를 조회한다")
        @Test
        fun `returns rank from RDB when cache miss`() {
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
            val rank = productRankingRdbReader.findRankByProductId(query, 102L)

            // then
            assertThat(rank).isEqualTo(2)
        }
    }

    private fun getCacheKey(period: RankingPeriod, instant: Instant): String {
        val baseKey = rankingKeyGenerator.bucketKey(period, instant)
        return "$baseKey$CACHE_SUFFIX"
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
