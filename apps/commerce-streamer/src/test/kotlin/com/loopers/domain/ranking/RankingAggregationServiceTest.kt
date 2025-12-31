package com.loopers.domain.ranking

import com.loopers.support.idempotency.EventHandledRepository
import com.loopers.support.idempotency.IdempotencyResult
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@DisplayName("RankingAggregationService 단위 테스트")
class RankingAggregationServiceTest {

    private lateinit var metricRepository: ProductHourlyMetricRepository
    private lateinit var rankingWriter: ProductRankingWriter
    private lateinit var rankingReader: ProductRankingReader
    private lateinit var rankingWeightRepository: RankingWeightRepository
    private lateinit var scoreCalculator: RankingScoreCalculator
    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var service: RankingAggregationService

    @BeforeEach
    fun setUp() {
        metricRepository = mockk()
        rankingWriter = mockk(relaxed = true)
        rankingReader = mockk()
        rankingWeightRepository = mockk()
        scoreCalculator = RankingScoreCalculator()
        eventHandledRepository = mockk()

        service = RankingAggregationService(
            metricRepository = metricRepository,
            rankingWriter = rankingWriter,
            rankingReader = rankingReader,
            rankingWeightRepository = rankingWeightRepository,
            scoreCalculator = scoreCalculator,
            eventHandledRepository = eventHandledRepository,
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
                        orderCountDelta = 3,
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
                            rows[0].orderCount == 3L &&
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
                orderCount = 10,
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
                orderCount = 0,
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
                orderCount = 0,
                orderAmount = BigDecimal.ZERO,
            )

            // Previous: 200 views -> 200 * 0.10 = 20 points
            val previousMetric = ProductHourlyMetric.create(
                statHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul")),
                productId = 1L,
                viewCount = 200,
                likeCount = 0,
                orderCount = 0,
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
                orderCount = 0,
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

    @DisplayName("accumulateViewMetric 멱등성 테스트")
    @Nested
    inner class AccumulateViewMetricIdempotency {

        @DisplayName("새 이벤트는 버퍼에 축적되고 멱등성 키가 저장된다")
        @Test
        fun `new event is accumulated and idempotency key is saved`() {
            // given
            val command = AccumulateViewMetricCommand(
                eventId = "event-123",
                productId = 1L,
                occurredAt = Instant.now(),
            )
            every { eventHandledRepository.existsByIdempotencyKey("ranking:view:event-123") } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.accumulateViewMetric(command)

            // then
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey("ranking:view:event-123") }
            verify(exactly = 1) { eventHandledRepository.save(match { it.idempotencyKey == "ranking:view:event-123" }) }
        }

        @DisplayName("중복 이벤트는 버퍼에 축적되지 않는다")
        @Test
        fun `duplicate event is not accumulated`() {
            // given
            val command = AccumulateViewMetricCommand(
                eventId = "event-123",
                productId = 1L,
                occurredAt = Instant.now(),
            )
            every { eventHandledRepository.existsByIdempotencyKey("ranking:view:event-123") } returns true

            // when
            service.accumulateViewMetric(command)

            // then
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey("ranking:view:event-123") }
            verify(exactly = 0) { eventHandledRepository.save(any()) }
        }
    }

    @DisplayName("accumulateLikeCreatedMetric 멱등성 테스트")
    @Nested
    inner class AccumulateLikeCreatedMetricIdempotency {

        @DisplayName("새 이벤트는 버퍼에 축적되고 멱등성 키가 저장된다")
        @Test
        fun `new event is accumulated and idempotency key is saved`() {
            // given
            val command = AccumulateLikeCreatedMetricCommand(
                eventId = "like-event-456",
                productId = 2L,
                occurredAt = Instant.now(),
            )
            every { eventHandledRepository.existsByIdempotencyKey("ranking:like-created:like-event-456") } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.accumulateLikeCreatedMetric(command)

            // then
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey("ranking:like-created:like-event-456") }
            verify(exactly = 1) { eventHandledRepository.save(match { it.idempotencyKey == "ranking:like-created:like-event-456" }) }
        }

        @DisplayName("중복 이벤트는 버퍼에 축적되지 않는다")
        @Test
        fun `duplicate event is not accumulated`() {
            // given
            val command = AccumulateLikeCreatedMetricCommand(
                eventId = "like-event-456",
                productId = 2L,
                occurredAt = Instant.now(),
            )
            every { eventHandledRepository.existsByIdempotencyKey("ranking:like-created:like-event-456") } returns true

            // when
            service.accumulateLikeCreatedMetric(command)

            // then
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey("ranking:like-created:like-event-456") }
            verify(exactly = 0) { eventHandledRepository.save(any()) }
        }
    }

    @DisplayName("accumulateLikeCanceledMetric 멱등성 테스트")
    @Nested
    inner class AccumulateLikeCanceledMetricIdempotency {

        @DisplayName("새 이벤트는 버퍼에 축적되고 멱등성 키가 저장된다")
        @Test
        fun `new event is accumulated and idempotency key is saved`() {
            // given
            val command = AccumulateLikeCanceledMetricCommand(
                eventId = "cancel-event-789",
                productId = 3L,
                occurredAt = Instant.now(),
            )
            every { eventHandledRepository.existsByIdempotencyKey("ranking:like-canceled:cancel-event-789") } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.accumulateLikeCanceledMetric(command)

            // then
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey("ranking:like-canceled:cancel-event-789") }
            verify(exactly = 1) { eventHandledRepository.save(match { it.idempotencyKey == "ranking:like-canceled:cancel-event-789" }) }
        }

        @DisplayName("중복 이벤트는 버퍼에 축적되지 않는다")
        @Test
        fun `duplicate event is not accumulated`() {
            // given
            val command = AccumulateLikeCanceledMetricCommand(
                eventId = "cancel-event-789",
                productId = 3L,
                occurredAt = Instant.now(),
            )
            every { eventHandledRepository.existsByIdempotencyKey("ranking:like-canceled:cancel-event-789") } returns true

            // when
            service.accumulateLikeCanceledMetric(command)

            // then
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey("ranking:like-canceled:cancel-event-789") }
            verify(exactly = 0) { eventHandledRepository.save(any()) }
        }
    }

    @DisplayName("accumulateOrderPaidMetric 멱등성 테스트")
    @Nested
    inner class AccumulateOrderPaidMetricIdempotency {

        @DisplayName("새 이벤트는 모든 아이템이 버퍼에 축적되고 멱등성 키가 저장된다")
        @Test
        fun `new event accumulates all items and saves idempotency key`() {
            // given
            val command = AccumulateOrderPaidMetricCommand(
                eventId = "order-event-999",
                items = listOf(
                    AccumulateOrderPaidMetricCommand.Item(productId = 1L, orderAmount = BigDecimal("1000")),
                    AccumulateOrderPaidMetricCommand.Item(productId = 2L, orderAmount = BigDecimal("2000")),
                ),
                occurredAt = Instant.now(),
            )
            every { eventHandledRepository.existsByIdempotencyKey("ranking:order-paid:order-event-999") } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.accumulateOrderPaidMetric(command)

            // then
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey("ranking:order-paid:order-event-999") }
            verify(exactly = 1) { eventHandledRepository.save(match { it.idempotencyKey == "ranking:order-paid:order-event-999" }) }
        }

        @DisplayName("중복 이벤트는 버퍼에 축적되지 않는다")
        @Test
        fun `duplicate event is not accumulated`() {
            // given
            val command = AccumulateOrderPaidMetricCommand(
                eventId = "order-event-999",
                items = listOf(
                    AccumulateOrderPaidMetricCommand.Item(productId = 1L, orderAmount = BigDecimal("1000")),
                ),
                occurredAt = Instant.now(),
            )
            every { eventHandledRepository.existsByIdempotencyKey("ranking:order-paid:order-event-999") } returns true

            // when
            service.accumulateOrderPaidMetric(command)

            // then
            verify(exactly = 1) { eventHandledRepository.existsByIdempotencyKey("ranking:order-paid:order-event-999") }
            verify(exactly = 0) { eventHandledRepository.save(any()) }
        }
    }
}
