package com.loopers.domain.ranking

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@DisplayName("RankingAggregationService 단위 테스트")
class RankingAggregationServiceTest {

    private lateinit var metricRepository: ProductHourlyMetricRepository
    private lateinit var dailyMetricRepository: ProductDailyMetricRepository
    private lateinit var rankingWriter: ProductRankingWriter
    private lateinit var rankingWeightRepository: RankingWeightRepository
    private lateinit var scoreCalculator: RankingScoreCalculator
    private lateinit var service: RankingAggregationService

    @BeforeEach
    fun setUp() {
        metricRepository = mockk()
        dailyMetricRepository = mockk(relaxed = true)
        rankingWriter = mockk(relaxed = true)
        rankingWeightRepository = mockk()
        scoreCalculator = RankingScoreCalculator()

        service = RankingAggregationService(
            metricRepository = metricRepository,
            dailyMetricRepository = dailyMetricRepository,
            rankingWriter = rankingWriter,
            rankingWeightRepository = rankingWeightRepository,
            scoreCalculator = scoreCalculator,
        )
    }

    @DisplayName("accumulateMetrics 테스트")
    @Nested
    inner class AccumulateMetricsTest {

        @DisplayName("빈 items 리스트는 DB 호출하지 않는다")
        @Test
        fun `empty items list does not call repository`() {
            // given
            val command = AccumulateMetricsCommand(items = emptyList())

            // when
            service.accumulateMetrics(command)

            // then
            verify(exactly = 0) { metricRepository.batchAccumulateCounts(any()) }
        }

        @DisplayName("items를 ProductHourlyMetricRow로 변환하여 repository에 저장한다")
        @Test
        fun `converts items to ProductHourlyMetricRow and saves to repository`() {
            // given
            val statHour = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.HOURS)
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

            every { metricRepository.batchAccumulateCounts(any()) } just Runs

            // when
            service.accumulateMetrics(command)

            // then
            verify(exactly = 1) {
                metricRepository.batchAccumulateCounts(
                    match { rows ->
                        rows.size == 1 &&
                            rows[0].productId == 1L &&
                            rows[0].viewCount == 10L &&
                            rows[0].likeCount == 3L && // 5 - 2
                            rows[0].orderAmount == BigDecimal("1000.00")
                    },
                )
            }
        }

        @DisplayName("여러 items를 한 번에 저장한다")
        @Test
        fun `saves multiple items at once`() {
            // given
            val statHour = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).truncatedTo(ChronoUnit.HOURS)
            val command = AccumulateMetricsCommand(
                items = listOf(
                    AccumulateMetricsCommand.Item(
                        productId = 1L,
                        statHour = statHour,
                        viewDelta = 1,
                    ),
                    AccumulateMetricsCommand.Item(
                        productId = 2L,
                        statHour = statHour,
                        viewDelta = 2,
                    ),
                    AccumulateMetricsCommand.Item(
                        productId = 3L,
                        statHour = statHour,
                        viewDelta = 3,
                    ),
                ),
            )

            every { metricRepository.batchAccumulateCounts(any()) } just Runs

            // when
            service.accumulateMetrics(command)

            // then
            verify(exactly = 1) {
                metricRepository.batchAccumulateCounts(
                    match { rows -> rows.size == 3 },
                )
            }
        }
    }

    @DisplayName("calculateAndUpdateScores 테스트")
    @Nested
    inner class CalculateAndUpdateScoresTest {

        @DisplayName("현재와 이전 버킷 모두 비어있으면 Redis 업데이트하지 않는다")
        @Test
        fun `does not update Redis when both buckets are empty`() {
            // given
            every { metricRepository.findAllByStatHour(any()) } returns emptyList()

            // when
            service.calculateAndUpdateScores()

            // then
            verify(exactly = 0) { rankingWriter.replaceAll(any(), any()) }
        }

        @DisplayName("현재 버킷의 메트릭으로 점수를 계산하여 Redis에 저장한다")
        @Test
        fun `calculates scores from current bucket and saves to Redis`() {
            // given
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            val currentMetric = ProductHourlyMetric.create(
                statHour = ZonedDateTime.ofInstant(currentHour, ZoneId.of("Asia/Seoul")),
                productId = 1L,
                viewCount = 100,
                likeCount = 50,
                orderAmount = BigDecimal("5000.00"),
            )

            val weights = RankingWeight.fallback()

            every { metricRepository.findAllByStatHour(currentHour) } returns listOf(currentMetric)
            every { metricRepository.findAllByStatHour(previousHour) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns weights

            // when
            service.calculateAndUpdateScores()

            // then
            verify(exactly = 1) { rankingWriter.replaceAll(any(), any()) }
        }

        @DisplayName("이전 버킷에만 있는 상품도 점수 계산에 포함한다 (cold start prevention)")
        @Test
        fun `includes products only in previous bucket`() {
            // given
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            val previousMetric = ProductHourlyMetric.create(
                statHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul")),
                productId = 99L,
                viewCount = 100,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )

            val weights = RankingWeight.fallback()

            every { metricRepository.findAllByStatHour(currentHour) } returns emptyList()
            every { metricRepository.findAllByStatHour(previousHour) } returns listOf(previousMetric)
            every { rankingWeightRepository.findLatest() } returns weights

            var capturedScores: Map<Long, Score>? = null
            every { rankingWriter.replaceAll(any(), any()) } answers {
                capturedScores = secondArg()
            }

            // when
            service.calculateAndUpdateScores()

            // then
            assertThat(capturedScores).isNotNull
            assertThat(capturedScores).containsKey(99L)
            // Previous only: 100 * 0.10 (viewWeight) * 0.1 (decay) = 1.0
            assertThat(capturedScores!![99L]!!.value).isEqualByComparingTo(BigDecimal("1.00"))
        }

        @DisplayName("감쇠 공식을 적용한다: previous * 0.1 + current * 0.9")
        @Test
        fun `applies decay formula - previous 0_1 plus current 0_9`() {
            // given
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            // Current: 100 views -> 100 * 0.10 = 10 points
            val currentMetric = ProductHourlyMetric.create(
                statHour = ZonedDateTime.ofInstant(currentHour, ZoneId.of("Asia/Seoul")),
                productId = 1L,
                viewCount = 100,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )

            // Previous: 200 views -> 200 * 0.10 = 20 points
            val previousMetric = ProductHourlyMetric.create(
                statHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul")),
                productId = 1L,
                viewCount = 200,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )

            val weights = RankingWeight.fallback()

            every { metricRepository.findAllByStatHour(currentHour) } returns listOf(currentMetric)
            every { metricRepository.findAllByStatHour(previousHour) } returns listOf(previousMetric)
            every { rankingWeightRepository.findLatest() } returns weights

            var capturedScores: Map<Long, Score>? = null
            every { rankingWriter.replaceAll(any(), any()) } answers {
                capturedScores = secondArg()
            }

            // when
            service.calculateAndUpdateScores()

            // then
            // Expected: 20 * 0.1 + 10 * 0.9 = 2 + 9 = 11
            assertThat(capturedScores).isNotNull
            assertThat(capturedScores!![1L]!!.value).isEqualByComparingTo(BigDecimal("11.00"))
        }

        @DisplayName("가중치가 없으면 fallback 값을 사용한다")
        @Test
        fun `uses fallback weight when no weight configured`() {
            // given
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            val currentMetric = ProductHourlyMetric.create(
                statHour = ZonedDateTime.ofInstant(currentHour, ZoneId.of("Asia/Seoul")),
                productId = 1L,
                viewCount = 10,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )

            every { metricRepository.findAllByStatHour(currentHour) } returns listOf(currentMetric)
            every { metricRepository.findAllByStatHour(previousHour) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns null

            var capturedScores: Map<Long, Score>? = null
            every { rankingWriter.replaceAll(any(), any()) } answers {
                capturedScores = secondArg()
            }

            // when
            service.calculateAndUpdateScores()

            // then - fallback viewWeight is 0.10
            // 10 * 0.10 * 0.9 = 0.9
            assertThat(capturedScores).isNotNull
            assertThat(capturedScores!![1L]!!.value).isEqualByComparingTo(BigDecimal("0.90"))
        }
    }

    @DisplayName("rollupHourlyToDaily 테스트")
    @Nested
    inner class RollupHourlyToDailyTest {

        @DisplayName("시간별 메트릭이 없으면 아무 작업도 하지 않는다")
        @Test
        fun `does nothing when no hourly metrics exist`() {
            // given
            val date = LocalDate.now()
            every { metricRepository.findAllByDate(date) } returns emptyList()

            // when
            service.rollupHourlyToDaily(date)

            // then
            verify(exactly = 0) { dailyMetricRepository.upsertFromHourly(any()) }
        }

        @DisplayName("시간별 메트릭을 상품별로 합산하여 일별 메트릭으로 저장한다")
        @Test
        fun `aggregates hourly metrics by product and saves as daily metrics`() {
            // given
            val date = LocalDate.now()
            val statHour1 = date.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul"))
            val statHour2 = date.atTime(11, 0).atZone(ZoneId.of("Asia/Seoul"))

            val hourlyMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = statHour1,
                    productId = 1L,
                    viewCount = 100,
                    likeCount = 10,
                    orderAmount = BigDecimal("1000.00"),
                ),
                ProductHourlyMetric.create(
                    statHour = statHour2,
                    productId = 1L,
                    viewCount = 200,
                    likeCount = 20,
                    orderAmount = BigDecimal("2000.00"),
                ),
            )

            every { metricRepository.findAllByDate(date) } returns hourlyMetrics

            var capturedDailyMetrics: List<ProductDailyMetric>? = null
            every { dailyMetricRepository.upsertFromHourly(any()) } answers {
                capturedDailyMetrics = firstArg()
            }

            // when
            service.rollupHourlyToDaily(date)

            // then
            assertThat(capturedDailyMetrics).isNotNull
            assertThat(capturedDailyMetrics).hasSize(1)

            val dailyMetric = capturedDailyMetrics!![0]
            assertThat(dailyMetric.productId).isEqualTo(1L)
            assertThat(dailyMetric.viewCount).isEqualTo(300L) // 100 + 200
            assertThat(dailyMetric.likeCount).isEqualTo(30L) // 10 + 20
            assertThat(dailyMetric.orderAmount).isEqualByComparingTo(BigDecimal("3000.00")) // 1000 + 2000
        }

        @DisplayName("여러 상품의 시간별 메트릭을 각각 합산한다")
        @Test
        fun `aggregates hourly metrics for multiple products`() {
            // given
            val date = LocalDate.now()
            val statHour = date.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul"))

            val hourlyMetrics = listOf(
                ProductHourlyMetric.create(statHour = statHour, productId = 1L, viewCount = 100),
                ProductHourlyMetric.create(statHour = statHour, productId = 2L, viewCount = 200),
            )

            every { metricRepository.findAllByDate(date) } returns hourlyMetrics

            var capturedDailyMetrics: List<ProductDailyMetric>? = null
            every { dailyMetricRepository.upsertFromHourly(any()) } answers {
                capturedDailyMetrics = firstArg()
            }

            // when
            service.rollupHourlyToDaily(date)

            // then
            assertThat(capturedDailyMetrics).hasSize(2)
            assertThat(capturedDailyMetrics!!.map { it.productId }).containsExactlyInAnyOrder(1L, 2L)
        }
    }

    @DisplayName("calculateDailyRankings 테스트")
    @Nested
    inner class CalculateDailyRankingsTest {

        @DisplayName("일별 메트릭이 없으면 아무 작업도 하지 않는다")
        @Test
        fun `does nothing when no daily metrics exist`() {
            // given
            val date = LocalDate.now()
            every { dailyMetricRepository.findAllByStatDate(date) } returns emptyList()

            // when
            service.calculateDailyRankings(date)

            // then
            verify(exactly = 0) { rankingWriter.replaceAll(any(), any()) }
        }

        @DisplayName("일별 메트릭으로 점수를 계산하여 Redis에 저장한다")
        @Test
        fun `calculates scores from daily metrics and saves to Redis`() {
            // given
            val date = LocalDate.now()
            val dailyMetric = ProductDailyMetric.create(
                statDate = date,
                productId = 1L,
                viewCount = 100,
                likeCount = 50,
                orderAmount = BigDecimal("5000.00"),
            )

            val weights = RankingWeight.fallback()

            every { dailyMetricRepository.findAllByStatDate(date) } returns listOf(dailyMetric)
            every { rankingWeightRepository.findLatest() } returns weights

            var capturedBucketKey: String? = null
            var capturedScores: Map<Long, Score>? = null
            every { rankingWriter.replaceAll(any(), any()) } answers {
                capturedBucketKey = firstArg()
                capturedScores = secondArg()
            }

            // when
            service.calculateDailyRankings(date)

            // then
            assertThat(capturedBucketKey).isEqualTo(RankingKeyGenerator.dailyBucketKey(date))
            assertThat(capturedScores).isNotNull
            assertThat(capturedScores).containsKey(1L)
        }

        @DisplayName("가중치가 없으면 fallback 값을 사용한다")
        @Test
        fun `uses fallback weight when no weight configured for daily`() {
            // given
            val date = LocalDate.now()
            val dailyMetric = ProductDailyMetric.create(
                statDate = date,
                productId = 1L,
                viewCount = 10,
                likeCount = 0,
                orderAmount = BigDecimal.ZERO,
            )

            every { dailyMetricRepository.findAllByStatDate(date) } returns listOf(dailyMetric)
            every { rankingWeightRepository.findLatest() } returns null

            var capturedScores: Map<Long, Score>? = null
            every { rankingWriter.replaceAll(any(), any()) } answers {
                capturedScores = secondArg()
            }

            // when
            service.calculateDailyRankings(date)

            // then - fallback viewWeight is 0.10
            // 10 * 0.10 = 1.0
            assertThat(capturedScores).isNotNull
            assertThat(capturedScores!![1L]!!.value).isEqualByComparingTo(BigDecimal("1.00"))
        }
    }
}
