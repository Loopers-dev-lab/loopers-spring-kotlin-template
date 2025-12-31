package com.loopers.domain.ranking

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

@SpringBootTest
@DisplayName("RankingService 통합 테스트")
class RankingServiceIntegrationTest @Autowired constructor(
    private val rankingService: RankingService,
    private val rankingWeightRepository: RankingWeightRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val redisTemplate: RedisTemplate<String, String>,
) {

    private val zSetOps = redisTemplate.opsForZSet()

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("findWeight()")
    @Nested
    inner class FindWeight {

        @DisplayName("저장된 가중치가 있으면 최신 가중치를 반환한다")
        @Test
        fun `returns latest weight when exists`() {
            // given
            rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.15"),
                    likeWeight = BigDecimal("0.25"),
                    orderWeight = BigDecimal("0.55"),
                ),
            )

            // when
            val result = rankingService.findWeight()

            // then
            assertThat(result.viewWeight).isEqualByComparingTo(BigDecimal("0.15"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.25"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.55"))
        }

        @DisplayName("저장된 가중치가 없으면 fallback 가중치를 반환한다")
        @Test
        fun `returns fallback weight when not exists`() {
            // when
            val result = rankingService.findWeight()

            // then
            assertThat(result.viewWeight).isEqualByComparingTo(BigDecimal("0.10"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.20"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.60"))
        }
    }

    @DisplayName("updateWeight()")
    @Nested
    inner class UpdateWeight {

        @DisplayName("기존 가중치가 있으면 업데이트하고 저장한다")
        @Test
        fun `updates existing weight and saves`() {
            // given
            rankingWeightRepository.save(
                RankingWeight.create(
                    viewWeight = BigDecimal("0.10"),
                    likeWeight = BigDecimal("0.20"),
                    orderWeight = BigDecimal("0.60"),
                ),
            )

            // when
            val result = rankingService.updateWeight(
                viewWeight = BigDecimal("0.30"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.40"),
            )

            // then
            assertThat(result.viewWeight).isEqualByComparingTo(BigDecimal("0.30"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.30"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.40"))

            // verify persisted
            val latest = rankingWeightRepository.findLatest()
            assertThat(latest).isNotNull
            assertThat(latest!!.viewWeight).isEqualByComparingTo(BigDecimal("0.30"))
            assertThat(latest.likeWeight).isEqualByComparingTo(BigDecimal("0.30"))
            assertThat(latest.orderWeight).isEqualByComparingTo(BigDecimal("0.40"))
        }

        @DisplayName("기존 가중치가 없으면 새로 생성하고 저장한다")
        @Test
        fun `creates new weight when not exists and saves`() {
            // when
            val result = rankingService.updateWeight(
                viewWeight = BigDecimal("0.25"),
                likeWeight = BigDecimal("0.35"),
                orderWeight = BigDecimal("0.40"),
            )

            // then
            assertThat(result.id).isGreaterThan(0L)
            assertThat(result.viewWeight).isEqualByComparingTo(BigDecimal("0.25"))
            assertThat(result.likeWeight).isEqualByComparingTo(BigDecimal("0.35"))
            assertThat(result.orderWeight).isEqualByComparingTo(BigDecimal("0.40"))

            // verify persisted
            val latest = rankingWeightRepository.findLatest()
            assertThat(latest).isNotNull
            assertThat(latest!!.viewWeight).isEqualByComparingTo(BigDecimal("0.25"))
        }
    }

    @DisplayName("findRankings()")
    @Nested
    inner class FindRankings {

        private val testBucketKey = "ranking:products:2025011514"
        private val fallbackBucketKey = "ranking:products:2025011513"

        @DisplayName("Redis에서 랭킹을 조회하여 반환한다")
        @Test
        fun `returns rankings from Redis`() {
            // given
            zSetOps.add(testBucketKey, "101", 300.0)
            zSetOps.add(testBucketKey, "102", 200.0)
            zSetOps.add(testBucketKey, "103", 100.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = testBucketKey,
                fallbackKey = fallbackBucketKey,
                offset = 0,
                limit = 10,
            )

            // when
            val result = rankingService.findRankings(query)

            // then
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(101L)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[1].productId).isEqualTo(102L)
            assertThat(result[1].rank).isEqualTo(2)
            assertThat(result[2].productId).isEqualTo(103L)
            assertThat(result[2].rank).isEqualTo(3)
        }

        @DisplayName("현재 버킷이 비어있고 첫 페이지이면 fallback 버킷에서 조회한다")
        @Test
        fun `uses fallback bucket when current bucket is empty and offset is 0`() {
            // given - fallback 버킷에만 데이터가 있음
            zSetOps.add(fallbackBucketKey, "201", 500.0)
            zSetOps.add(fallbackBucketKey, "202", 400.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = testBucketKey,
                fallbackKey = fallbackBucketKey,
                offset = 0,
                limit = 10,
            )

            // when
            val result = rankingService.findRankings(query)

            // then
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(201L)
            assertThat(result[1].productId).isEqualTo(202L)
        }

        @DisplayName("현재 버킷이 비어있어도 두 번째 페이지 이상이면 fallback을 사용하지 않는다")
        @Test
        fun `does not use fallback when not first page`() {
            // given - fallback 버킷에만 데이터가 있음
            zSetOps.add(fallbackBucketKey, "201", 500.0)

            val query = RankingQuery(
                period = RankingPeriod.HOURLY,
                bucketKey = testBucketKey,
                fallbackKey = fallbackBucketKey,
                offset = 10,
                limit = 10,
            )

            // when
            val result = rankingService.findRankings(query)

            // then
            assertThat(result).isEmpty()
        }
    }
}
