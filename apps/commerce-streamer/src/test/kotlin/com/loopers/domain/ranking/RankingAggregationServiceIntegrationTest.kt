package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.ProductDailyMetricJpaRepository
import com.loopers.infrastructure.ranking.ProductHourlyMetricJpaRepository
import com.loopers.infrastructure.ranking.RankingWeightJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@SpringBootTest
@DisplayName("RankingAggregationService 통합 테스트")
class RankingAggregationServiceIntegrationTest @Autowired constructor(
    private val rankingAggregationService: RankingAggregationService,
    private val rankingKeyGenerator: RankingKeyGenerator,
    private val productHourlyMetricJpaRepository: ProductHourlyMetricJpaRepository,
    private val productDailyMetricJpaRepository: ProductDailyMetricJpaRepository,
    private val rankingWeightJpaRepository: RankingWeightJpaRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    private val zSetOps = redisTemplate.opsForZSet()
    private val seoulZone = ZoneId.of("Asia/Seoul")

    @BeforeEach
    fun setUp() {
        // 가중치 데이터 설정
        val weight = RankingWeight(
            viewWeight = BigDecimal("0.10"),
            likeWeight = BigDecimal("0.20"),
            orderWeight = BigDecimal("0.60"),
        )
        rankingWeightJpaRepository.save(weight)
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("accumulateMetrics 통합 테스트")
    @Nested
    inner class AccumulateMetricsIntegration {

        @DisplayName("배치 커맨드로 DB에 메트릭을 저장한다")
        @Test
        fun `saves metrics to DB via batch command`() {
            // given
            val statHour = ZonedDateTime.now(seoulZone).truncatedTo(ChronoUnit.HOURS)
            val command = AccumulateMetricsCommand(
                items = listOf(
                    AccumulateMetricsCommand.Item(
                        productId = 1L,
                        statHour = statHour,
                        viewDelta = 10,
                        likeCreatedDelta = 5,
                        likeCanceledDelta = 2,
                        orderAmountDelta = BigDecimal("1000.00"),
                    ),
                ),
            )

            // when
            rankingAggregationService.accumulateMetrics(command)

            // then
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].productId).isEqualTo(1L)
            assertThat(metrics[0].viewCount).isEqualTo(10L)
            assertThat(metrics[0].likeCount).isEqualTo(3L) // 5 - 2
            assertThat(metrics[0].orderAmount).isEqualByComparingTo(BigDecimal("1000.00"))
        }

        @DisplayName("여러 상품의 메트릭을 한 번에 저장한다")
        @Test
        fun `saves multiple products metrics at once`() {
            // given
            val statHour = ZonedDateTime.now(seoulZone).truncatedTo(ChronoUnit.HOURS)
            val command = AccumulateMetricsCommand(
                items = listOf(
                    AccumulateMetricsCommand.Item(
                        productId = 1L,
                        statHour = statHour,
                        viewDelta = 10,
                    ),
                    AccumulateMetricsCommand.Item(
                        productId = 2L,
                        statHour = statHour,
                        viewDelta = 20,
                    ),
                    AccumulateMetricsCommand.Item(
                        productId = 3L,
                        statHour = statHour,
                        viewDelta = 30,
                    ),
                ),
            )

            // when
            rankingAggregationService.accumulateMetrics(command)

            // then
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(3)
            assertThat(metrics.map { it.productId }).containsExactlyInAnyOrder(1L, 2L, 3L)
        }

        @DisplayName("동일 상품-시간에 대해 누적된다 (upsert)")
        @Test
        fun `accumulates for same product-hour combination`() {
            // given
            val statHour = ZonedDateTime.now(seoulZone).truncatedTo(ChronoUnit.HOURS)
            val command1 = AccumulateMetricsCommand(
                items = listOf(
                    AccumulateMetricsCommand.Item(
                        productId = 1L,
                        statHour = statHour,
                        viewDelta = 10,
                    ),
                ),
            )
            val command2 = AccumulateMetricsCommand(
                items = listOf(
                    AccumulateMetricsCommand.Item(
                        productId = 1L,
                        statHour = statHour,
                        viewDelta = 5,
                    ),
                ),
            )

            // when
            rankingAggregationService.accumulateMetrics(command1)
            rankingAggregationService.accumulateMetrics(command2)

            // then
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].viewCount).isEqualTo(15L) // 10 + 5
        }
    }

    @DisplayName("calculateAndUpdateScores 통합 테스트")
    @Nested
    inner class CalculateAndUpdateScoresIntegration {

        @DisplayName("현재 버킷 메트릭을 기반으로 Redis에 점수를 저장한다")
        @Test
        fun `saves scores to Redis based on current bucket metrics`() {
            // given - 현재 시간 버킷에 메트릭 저장
            val currentHour = ZonedDateTime.now(seoulZone).truncatedTo(ChronoUnit.HOURS)
            val metric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 1L,
                viewCount = 100,
                likeCount = 50,
                orderAmount = BigDecimal("1000.00"),
            )
            productHourlyMetricJpaRepository.save(metric)

            // when
            rankingAggregationService.calculateAndUpdateScores()

            // then
            val bucketKey = rankingKeyGenerator.currentBucketKey()
            val score = zSetOps.score(bucketKey, "1")
            assertThat(score).isNotNull

            // Score = (100 * 0.10 + 50 * 0.20 + 1000 * 0.60) * 0.9 = (10 + 10 + 600) * 0.9 = 620 * 0.9 = 558
            assertThat(score).isEqualTo(558.0)
        }

        @DisplayName("감쇠 공식을 적용한다: previous * 0.1 + current * 0.9")
        @Test
        fun `applies decay formula - previous 0_1 plus current 0_9`() {
            // given
            val currentHour = ZonedDateTime.now(seoulZone).truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minusHours(1)

            // Current bucket: 100 views -> 100 * 0.10 = 10 points
            val currentMetric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 1L,
                viewCount = 100,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )

            // Previous bucket: 200 views -> 200 * 0.10 = 20 points
            val previousMetric = ProductHourlyMetric.create(
                statHour = previousHour,
                productId = 1L,
                viewCount = 200,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )

            productHourlyMetricJpaRepository.saveAll(listOf(currentMetric, previousMetric))

            // when
            rankingAggregationService.calculateAndUpdateScores()

            // then
            val bucketKey = rankingKeyGenerator.currentBucketKey()
            val score = zSetOps.score(bucketKey, "1")

            // Expected: 20 * 0.1 + 10 * 0.9 = 2 + 9 = 11
            assertThat(score).isEqualTo(11.0)
        }

        @DisplayName("이전 버킷에만 있는 상품도 점수 계산에 포함한다")
        @Test
        fun `includes products only in previous bucket`() {
            // given
            val previousHour = ZonedDateTime.now(seoulZone).truncatedTo(ChronoUnit.HOURS).minusHours(1)

            // Only in previous bucket: 100 views -> 100 * 0.10 = 10 points
            val previousMetric = ProductHourlyMetric.create(
                statHour = previousHour,
                productId = 99L,
                viewCount = 100,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )
            productHourlyMetricJpaRepository.save(previousMetric)

            // when
            rankingAggregationService.calculateAndUpdateScores()

            // then
            val bucketKey = rankingKeyGenerator.currentBucketKey()
            val score = zSetOps.score(bucketKey, "99")

            // Expected: 10 * 0.1 + 0 * 0.9 = 1.0
            assertThat(score).isEqualTo(1.0)
        }

        @DisplayName("여러 상품의 점수를 동시에 계산한다")
        @Test
        fun `calculates scores for multiple products`() {
            // given
            val currentHour = ZonedDateTime.now(seoulZone).truncatedTo(ChronoUnit.HOURS)

            val metric1 = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 1L,
                viewCount = 100,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )
            val metric2 = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 2L,
                viewCount = 200,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )
            productHourlyMetricJpaRepository.saveAll(listOf(metric1, metric2))

            // when
            rankingAggregationService.calculateAndUpdateScores()

            // then
            val bucketKey = rankingKeyGenerator.currentBucketKey()
            // Product 1: 100 * 0.10 * 0.9 = 9.0
            assertThat(zSetOps.score(bucketKey, "1")).isEqualTo(9.0)
            // Product 2: 200 * 0.10 * 0.9 = 18.0
            assertThat(zSetOps.score(bucketKey, "2")).isEqualTo(18.0)
        }

        @DisplayName("메트릭이 없으면 Redis를 업데이트하지 않는다")
        @Test
        fun `does not update Redis when no metrics exist`() {
            // given - no metrics in DB

            // when
            rankingAggregationService.calculateAndUpdateScores()

            // then
            val bucketKey = rankingKeyGenerator.currentBucketKey()
            assertThat(redisTemplate.hasKey(bucketKey)).isFalse()
        }
    }

    @DisplayName("rollupHourlyToDaily 통합 테스트")
    @Nested
    inner class RollupHourlyToDailyIntegration {

        @DisplayName("시간별 메트릭을 일별 메트릭으로 집계한다")
        @Test
        fun `aggregates hourly metrics to daily metrics`() {
            // given - 특정 날짜의 여러 시간대에 시간별 메트릭 저장
            val targetDate = LocalDate.now(seoulZone)
            val hour00 = targetDate.atStartOfDay(seoulZone)
            val hour12 = hour00.plusHours(12)

            val metric1 = ProductHourlyMetric.create(
                statHour = hour00,
                productId = 1L,
                viewCount = 100,
                likeCount = 10,
                orderAmount = BigDecimal("1000.00"),
            )
            val metric2 = ProductHourlyMetric.create(
                statHour = hour12,
                productId = 1L,
                viewCount = 50,
                likeCount = 5,
                orderAmount = BigDecimal("500.00"),
            )
            productHourlyMetricJpaRepository.saveAll(listOf(metric1, metric2))

            // when
            rankingAggregationService.rollupHourlyToDaily(targetDate)

            // then
            val dailyMetrics = productDailyMetricJpaRepository.findAllByStatDate(targetDate)
            assertThat(dailyMetrics).hasSize(1)
            assertThat(dailyMetrics[0].productId).isEqualTo(1L)
            assertThat(dailyMetrics[0].viewCount).isEqualTo(150L) // 100 + 50
            assertThat(dailyMetrics[0].likeCount).isEqualTo(15L) // 10 + 5
            assertThat(dailyMetrics[0].orderAmount).isEqualByComparingTo(BigDecimal("1500.00")) // 1000 + 500
        }

        @DisplayName("여러 상품의 시간별 메트릭을 각각 일별로 집계한다")
        @Test
        fun `aggregates multiple products hourly metrics to daily`() {
            // given
            val targetDate = LocalDate.now(seoulZone)
            val hour00 = targetDate.atStartOfDay(seoulZone)

            val metric1 = ProductHourlyMetric.create(
                statHour = hour00,
                productId = 1L,
                viewCount = 100,
                likeCount = 10,
                orderAmount = BigDecimal("1000.00"),
            )
            val metric2 = ProductHourlyMetric.create(
                statHour = hour00,
                productId = 2L,
                viewCount = 200,
                likeCount = 20,
                orderAmount = BigDecimal("2000.00"),
            )
            productHourlyMetricJpaRepository.saveAll(listOf(metric1, metric2))

            // when
            rankingAggregationService.rollupHourlyToDaily(targetDate)

            // then
            val dailyMetrics = productDailyMetricJpaRepository.findAllByStatDate(targetDate)
            assertThat(dailyMetrics).hasSize(2)
            assertThat(dailyMetrics.map { it.productId }).containsExactlyInAnyOrder(1L, 2L)
        }

        @DisplayName("시간별 메트릭이 없으면 일별 메트릭을 생성하지 않는다")
        @Test
        fun `does not create daily metrics when no hourly metrics exist`() {
            // given - no hourly metrics for target date
            val targetDate = LocalDate.now(seoulZone)

            // when
            rankingAggregationService.rollupHourlyToDaily(targetDate)

            // then
            val dailyMetrics = productDailyMetricJpaRepository.findAllByStatDate(targetDate)
            assertThat(dailyMetrics).isEmpty()
        }
    }

    @DisplayName("calculateDailyRankings 통합 테스트")
    @Nested
    inner class CalculateDailyRankingsIntegration {

        @DisplayName("일별 메트릭을 기반으로 Redis에 일별 랭킹 점수를 저장한다")
        @Test
        fun `saves daily rankings to Redis with correct scores`() {
            // given - 일별 메트릭 저장
            val targetDate = LocalDate.now(seoulZone)
            val dailyMetric = ProductDailyMetric.create(
                statDate = targetDate,
                productId = 1L,
                viewCount = 100,
                likeCount = 50,
                orderAmount = BigDecimal("1000.00"),
            )
            productDailyMetricJpaRepository.save(dailyMetric)

            // when
            rankingAggregationService.calculateDailyRankings(targetDate)

            // then
            val dailyBucketKey = rankingKeyGenerator.dailyBucketKey(targetDate)
            val score = zSetOps.score(dailyBucketKey, "1")
            assertThat(score).isNotNull

            // Score = 100 * 0.10 + 50 * 0.20 + 1000 * 0.60 = 10 + 10 + 600 = 620
            assertThat(score).isEqualTo(620.0)
        }

        @DisplayName("여러 상품의 일별 랭킹 점수를 계산한다")
        @Test
        fun `calculates daily rankings for multiple products`() {
            // given
            val targetDate = LocalDate.now(seoulZone)

            val dailyMetric1 = ProductDailyMetric.create(
                statDate = targetDate,
                productId = 1L,
                viewCount = 100,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )
            val dailyMetric2 = ProductDailyMetric.create(
                statDate = targetDate,
                productId = 2L,
                viewCount = 200,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )
            productDailyMetricJpaRepository.saveAll(listOf(dailyMetric1, dailyMetric2))

            // when
            rankingAggregationService.calculateDailyRankings(targetDate)

            // then
            val dailyBucketKey = rankingKeyGenerator.dailyBucketKey(targetDate)
            // Product 1: 100 * 0.10 = 10.0
            assertThat(zSetOps.score(dailyBucketKey, "1")).isEqualTo(10.0)
            // Product 2: 200 * 0.10 = 20.0
            assertThat(zSetOps.score(dailyBucketKey, "2")).isEqualTo(20.0)
        }

        @DisplayName("일별 메트릭이 없으면 Redis를 업데이트하지 않는다")
        @Test
        fun `does not update Redis when no daily metrics exist`() {
            // given - no daily metrics for target date
            val targetDate = LocalDate.now(seoulZone)

            // when
            rankingAggregationService.calculateDailyRankings(targetDate)

            // then
            val dailyBucketKey = rankingKeyGenerator.dailyBucketKey(targetDate)
            assertThat(redisTemplate.hasKey(dailyBucketKey)).isFalse()
        }
    }
}
