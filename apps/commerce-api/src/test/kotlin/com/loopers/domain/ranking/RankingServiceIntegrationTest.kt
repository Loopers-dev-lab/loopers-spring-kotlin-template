package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.MvProductRankMonthly
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository
import com.loopers.infrastructure.ranking.MvProductRankWeekly
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository
import com.loopers.infrastructure.ranking.RankingKeyGenerator
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
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest
@DisplayName("RankingService 통합 테스트")
class RankingServiceIntegrationTest @Autowired constructor(
    private val rankingService: RankingService,
    private val rankingWeightRepository: RankingWeightRepository,
    private val rankingKeyGenerator: RankingKeyGenerator,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val redisTemplate: RedisTemplate<String, String>,
    private val weeklyJpaRepository: MvProductRankWeeklyJpaRepository,
    private val monthlyJpaRepository: MvProductRankMonthlyJpaRepository,
) {

    private val zSetOps = redisTemplate.opsForZSet()

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")
    }

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

        @DisplayName("Redis에서 랭킹을 조회하여 반환한다")
        @Test
        fun `returns rankings from Redis`() {
            // given
            val bucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)
            zSetOps.add(bucketKey, "101", 300.0)
            zSetOps.add(bucketKey, "102", 200.0)
            zSetOps.add(bucketKey, "103", 100.0)

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = null,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

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
            val currentBucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)
            val fallbackBucketKey = rankingKeyGenerator.previousBucketKey(currentBucketKey)
            zSetOps.add(fallbackBucketKey, "201", 500.0)
            zSetOps.add(fallbackBucketKey, "202", 400.0)

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = null,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

            // then
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(201L)
            assertThat(result[1].productId).isEqualTo(202L)
        }

        @DisplayName("현재 버킷이 비어있어도 두 번째 페이지 이상이면 fallback을 사용하지 않는다")
        @Test
        fun `does not use fallback when not first page`() {
            // given - fallback 버킷에만 데이터가 있음
            val currentBucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)
            val fallbackBucketKey = rankingKeyGenerator.previousBucketKey(currentBucketKey)
            zSetOps.add(fallbackBucketKey, "201", 500.0)

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = null,
                page = 1,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("date가 지정되면 해당 date 버킷에서 조회한다")
        @Test
        fun `uses specified date bucket when date is provided`() {
            // given
            val date = "2025011514"
            val bucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, date)
            zSetOps.add(bucketKey, "101", 300.0)

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = date,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(101L)
        }

        @DisplayName("date가 지정되어도 버킷이 비어있으면 fallback을 시도한다 (AC-4)")
        @Test
        fun `uses fallback even when date is specified (AC-4)`() {
            // given - 지정된 date 버킷은 비어있고, 이전 버킷에 데이터가 있음
            val date = "2025011514"
            val specifiedBucketKey = rankingKeyGenerator.bucketKey(RankingPeriod.HOURLY, date)
            val previousBucketKey = rankingKeyGenerator.previousBucketKey(specifiedBucketKey)
            zSetOps.add(previousBucketKey, "201", 500.0)

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.HOURLY,
                date = date,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

            // then - 이제 fallback이 적용되어 데이터가 반환됨
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(201L)
        }

        @DisplayName("WEEKLY 랭킹을 RDB에서 조회한다")
        @Test
        fun `returns weekly rankings from RDB`() {
            // given
            val today = LocalDate.now(SEOUL_ZONE)
            saveWeeklyRanking(today, 1, 101L, BigDecimal("300.00"))
            saveWeeklyRanking(today, 2, 102L, BigDecimal("200.00"))
            saveWeeklyRanking(today, 3, 103L, BigDecimal("100.00"))

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.WEEKLY,
                date = null,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

            // then
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(101L)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[1].productId).isEqualTo(102L)
            assertThat(result[1].rank).isEqualTo(2)
            assertThat(result[2].productId).isEqualTo(103L)
            assertThat(result[2].rank).isEqualTo(3)
        }

        @DisplayName("MONTHLY 랭킹을 RDB에서 조회한다")
        @Test
        fun `returns monthly rankings from RDB`() {
            // given
            val today = LocalDate.now(SEOUL_ZONE)
            saveMonthlyRanking(today, 1, 201L, BigDecimal("500.00"))
            saveMonthlyRanking(today, 2, 202L, BigDecimal("400.00"))

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.MONTHLY,
                date = null,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

            // then
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(201L)
            assertThat(result[0].rank).isEqualTo(1)
            assertThat(result[1].productId).isEqualTo(202L)
            assertThat(result[1].rank).isEqualTo(2)
        }

        @DisplayName("WEEKLY 랭킹이 비어있으면 fallback으로 이전 주 조회")
        @Test
        fun `uses fallback for WEEKLY when current data is empty`() {
            // given - 현재 날짜에는 데이터가 없고, 7일 전 날짜에 데이터가 있음
            val today = LocalDate.now(SEOUL_ZONE)
            val previousWeek = today.minusDays(7)
            saveWeeklyRanking(previousWeek, 1, 301L, BigDecimal("400.00"))
            saveWeeklyRanking(previousWeek, 2, 302L, BigDecimal("300.00"))

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.WEEKLY,
                date = null,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

            // then - fallback으로 이전 주 데이터가 반환됨
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(301L)
            assertThat(result[1].productId).isEqualTo(302L)
        }

        @DisplayName("MONTHLY 랭킹이 비어있으면 fallback으로 이전 월 조회")
        @Test
        fun `uses fallback for MONTHLY when current data is empty`() {
            // given - 현재 날짜에는 데이터가 없고, 30일 전 날짜에 데이터가 있음
            val today = LocalDate.now(SEOUL_ZONE)
            val previousMonth = today.minusDays(30)
            saveMonthlyRanking(previousMonth, 1, 401L, BigDecimal("600.00"))

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.MONTHLY,
                date = null,
                page = 0,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

            // then - fallback으로 이전 월 데이터가 반환됨
            assertThat(result).hasSize(1)
            assertThat(result[0].productId).isEqualTo(401L)
        }

        @DisplayName("WEEKLY 두 번째 페이지에서는 fallback을 사용하지 않음")
        @Test
        fun `does not use fallback for WEEKLY when not first page`() {
            // given - 이전 주에만 데이터가 있음
            val today = LocalDate.now(SEOUL_ZONE)
            val previousWeek = today.minusDays(7)
            saveWeeklyRanking(previousWeek, 1, 501L, BigDecimal("500.00"))

            val command = RankingCommand.FindRankings(
                period = RankingPeriod.WEEKLY,
                date = null,
                page = 1,
                size = 10,
            )

            // when
            val result = rankingService.findRankings(command)

            // then - 두 번째 페이지에서는 fallback을 사용하지 않아 빈 리스트 반환
            assertThat(result).isEmpty()
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
