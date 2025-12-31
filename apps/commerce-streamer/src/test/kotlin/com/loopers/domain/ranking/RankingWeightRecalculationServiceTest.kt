package com.loopers.domain.ranking

import com.loopers.infrastructure.ranking.ProductHourlyMetricJpaRepository
import com.loopers.support.idempotency.EventHandledRepository
import com.loopers.support.idempotency.IdempotencyResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@DisplayName("RankingWeightRecalculationService 단위 테스트")
class RankingWeightRecalculationServiceTest {

    private lateinit var metricJpaRepository: ProductHourlyMetricJpaRepository
    private lateinit var rankingWeightRepository: RankingWeightRepository
    private lateinit var scoreCalculator: RankingScoreCalculator
    private lateinit var rankingWriter: ProductRankingWriter
    private lateinit var rankingReader: ProductRankingReader
    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var service: RankingWeightRecalculationService

    private val zoneId = ZoneId.of("Asia/Seoul")

    @BeforeEach
    fun setUp() {
        metricJpaRepository = mockk()
        rankingWeightRepository = mockk()
        scoreCalculator = RankingScoreCalculator()
        rankingWriter = mockk(relaxed = true)
        rankingReader = mockk()
        eventHandledRepository = mockk()

        service = RankingWeightRecalculationService(
            metricJpaRepository = metricJpaRepository,
            rankingWeightRepository = rankingWeightRepository,
            scoreCalculator = scoreCalculator,
            rankingWriter = rankingWriter,
            rankingReader = rankingReader,
            eventHandledRepository = eventHandledRepository,
        )
    }

    @DisplayName("멱등성 테스트")
    @Nested
    inner class Idempotency {

        @DisplayName("이미 처리된 이벤트는 스킵된다")
        @Test
        fun `skips already handled events`() {
            // given
            val command = RecalculateScoresCommand(eventId = "event-123")
            every {
                eventHandledRepository.existsByIdempotencyKey("ranking-weight-recalculation:event-123")
            } returns true

            // when
            service.recalculateScores(command)

            // then
            verify(exactly = 1) {
                eventHandledRepository.existsByIdempotencyKey("ranking-weight-recalculation:event-123")
            }
            verify(exactly = 0) { rankingWeightRepository.findLatest() }
            verify(exactly = 0) { rankingWriter.replaceAll(any(), any()) }
        }

        @DisplayName("새 이벤트는 처리되고 멱등성 키가 저장된다")
        @Test
        fun `processes new event and saves idempotency key`() {
            // given
            val command = RecalculateScoresCommand(eventId = "event-456")
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(currentHour, zoneId)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, zoneId)

            every {
                eventHandledRepository.existsByIdempotencyKey("ranking-weight-recalculation:event-456")
            } returns false
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns emptyList()
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingReader.exists(any()) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.recalculateScores(command)

            // then
            verify(exactly = 1) {
                eventHandledRepository.save(match { it.idempotencyKey == "ranking-weight-recalculation:event-456" })
            }
        }
    }

    @DisplayName("현재 버킷 재계산 테스트")
    @Nested
    inner class CurrentBucketRecalculation {

        @DisplayName("현재 버킷 점수에 이전 버킷 감쇠가 포함된다")
        @Test
        fun `includes decay from previous bucket in current bucket scores`() {
            // given
            val command = RecalculateScoresCommand(eventId = "event-789")
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(currentHour, zoneId)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, zoneId)

            val productId = 1L
            val currentMetric = ProductHourlyMetric.create(
                statHour = currentStatHour,
                productId = productId,
                viewCount = 100,
                likeCount = 10,
                orderCount = 1,
                orderAmount = BigDecimal("1000.00"),
            )
            val previousMetric = ProductHourlyMetric.create(
                statHour = previousStatHour,
                productId = productId,
                viewCount = 200,
                likeCount = 20,
                orderCount = 2,
                orderAmount = BigDecimal("2000.00"),
            )

            every {
                eventHandledRepository.existsByIdempotencyKey(any())
            } returns false
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(currentMetric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns listOf(previousMetric)
            every { rankingReader.exists(any()) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.recalculateScores(command)

            // then - verify replaceAll is called with correct scores
            verify(exactly = 1) {
                rankingWriter.replaceAll(
                    match { it == RankingKeyGenerator.currentBucketKey() },
                    match { scores ->
                        // Current: 100 * 0.1 + 10 * 0.2 + 1000 * 0.6 = 10 + 2 + 600 = 612
                        // Previous: 200 * 0.1 + 20 * 0.2 + 2000 * 0.6 = 20 + 4 + 1200 = 1224
                        // Decay: 1224 * 0.1 = 122.4
                        // Total: 612 + 122.4 = 734.4
                        scores[productId]?.value?.compareTo(BigDecimal("734.40")) == 0
                    },
                )
            }
        }

        @DisplayName("이전 버킷에만 있는 상품도 감쇠 적용하여 포함된다 (cold start 방지)")
        @Test
        fun `includes products only in previous bucket with decayed scores for cold start prevention`() {
            // given
            val command = RecalculateScoresCommand(eventId = "event-cold-start")
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(currentHour, zoneId)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, zoneId)

            val currentProductId = 1L
            val previousOnlyProductId = 2L

            val currentMetric = ProductHourlyMetric.create(
                statHour = currentStatHour,
                productId = currentProductId,
                viewCount = 100,
                likeCount = 10,
                orderCount = 1,
                orderAmount = BigDecimal("1000.00"),
            )
            val previousOnlyMetric = ProductHourlyMetric.create(
                statHour = previousStatHour,
                productId = previousOnlyProductId,
                viewCount = 500,
                likeCount = 50,
                orderCount = 5,
                orderAmount = BigDecimal("5000.00"),
            )

            every {
                eventHandledRepository.existsByIdempotencyKey(any())
            } returns false
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(currentMetric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns listOf(previousOnlyMetric)
            every { rankingReader.exists(any()) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.recalculateScores(command)

            // then - verify both products are in the scores
            verify(exactly = 1) {
                rankingWriter.replaceAll(
                    match { it == RankingKeyGenerator.currentBucketKey() },
                    match { scores ->
                        // Current product: 100 * 0.1 + 10 * 0.2 + 1000 * 0.6 = 612
                        // Previous only: 500 * 0.1 + 50 * 0.2 + 5000 * 0.6 = 50 + 10 + 3000 = 3060
                        // Decayed: 3060 * 0.1 = 306
                        scores.size == 2 &&
                            scores[currentProductId]?.value?.compareTo(BigDecimal("612.00")) == 0 &&
                            scores[previousOnlyProductId]?.value?.compareTo(BigDecimal("306.00")) == 0
                    },
                )
            }
        }
    }

    @DisplayName("다음 버킷 업데이트 테스트")
    @Nested
    inner class NextBucketUpdate {

        @DisplayName("다음 버킷이 존재하면 업데이트한다")
        @Test
        fun `updates next bucket if it exists`() {
            // given
            val command = RecalculateScoresCommand(eventId = "event-next-exists")
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(currentHour, zoneId)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, zoneId)
            val nextBucketKey = RankingKeyGenerator.nextBucketKey()
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()

            val productId = 1L
            val currentMetric = ProductHourlyMetric.create(
                statHour = currentStatHour,
                productId = productId,
                viewCount = 100,
                likeCount = 10,
                orderCount = 1,
                orderAmount = BigDecimal("1000.00"),
            )

            every {
                eventHandledRepository.existsByIdempotencyKey(any())
            } returns false
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(currentMetric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingReader.exists(nextBucketKey) } returns true
            every { rankingReader.getAllScores(currentBucketKey) } returns mapOf(productId to Score.of(612.0))
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.recalculateScores(command)

            // then - verify next bucket is updated with decayed scores
            verify(exactly = 1) {
                rankingWriter.replaceAll(
                    match { it == nextBucketKey },
                    match { scores ->
                        // 612 * 0.1 = 61.2
                        scores[productId]?.value?.compareTo(BigDecimal("61.20")) == 0
                    },
                )
            }
        }

        @DisplayName("다음 버킷이 존재하지 않으면 업데이트를 스킵한다")
        @Test
        fun `skips update if next bucket does not exist`() {
            // given
            val command = RecalculateScoresCommand(eventId = "event-next-not-exists")
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(currentHour, zoneId)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, zoneId)
            val nextBucketKey = RankingKeyGenerator.nextBucketKey()

            val productId = 1L
            val currentMetric = ProductHourlyMetric.create(
                statHour = currentStatHour,
                productId = productId,
                viewCount = 100,
                likeCount = 10,
                orderCount = 1,
                orderAmount = BigDecimal("1000.00"),
            )

            every {
                eventHandledRepository.existsByIdempotencyKey(any())
            } returns false
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(currentMetric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingReader.exists(nextBucketKey) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.recalculateScores(command)

            // then - verify replaceAll called only once (for current bucket, not next)
            verify(exactly = 1) {
                rankingWriter.replaceAll(match { it == RankingKeyGenerator.currentBucketKey() }, any())
            }
            verify(exactly = 0) {
                rankingWriter.replaceAll(match { it == nextBucketKey }, any())
            }
        }
    }

    @DisplayName("재시도 테스트")
    @Nested
    inner class RetryLogic {

        @DisplayName("replaceAll 실패 시 최대 3회 재시도한다")
        @Test
        fun `retries replaceAll up to 3 times on failure`() {
            // given
            val command = RecalculateScoresCommand(eventId = "event-retry")
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(currentHour, zoneId)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, zoneId)

            val productId = 1L
            val currentMetric = ProductHourlyMetric.create(
                statHour = currentStatHour,
                productId = productId,
                viewCount = 100,
                likeCount = 10,
                orderCount = 1,
                orderAmount = BigDecimal("1000.00"),
            )

            every {
                eventHandledRepository.existsByIdempotencyKey(any())
            } returns false
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(currentMetric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingReader.exists(any()) } returns false
            every { rankingWriter.replaceAll(any(), any()) } throws RuntimeException("Redis Error")

            // when & then - should not throw, exception is caught internally
            try {
                service.recalculateScores(command)
            } catch (e: Exception) {
                // Expected - exception after 3 retries
            }

            // then - replaceAll should be called 3 times (max retries)
            verify(exactly = 3) { rankingWriter.replaceAll(any(), any()) }
        }

        @DisplayName("두 번째 시도에서 성공하면 재시도를 중단한다")
        @Test
        fun `stops retrying when replaceAll succeeds on second try`() {
            // given
            val command = RecalculateScoresCommand(eventId = "event-retry-success")
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(currentHour, zoneId)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, zoneId)

            val productId = 1L
            val currentMetric = ProductHourlyMetric.create(
                statHour = currentStatHour,
                productId = productId,
                viewCount = 100,
                likeCount = 10,
                orderCount = 1,
                orderAmount = BigDecimal("1000.00"),
            )

            var callCount = 0
            every {
                eventHandledRepository.existsByIdempotencyKey(any())
            } returns false
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(currentMetric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingReader.exists(any()) } returns false
            every { rankingWriter.replaceAll(any(), any()) } answers {
                callCount++
                if (callCount == 1) {
                    throw RuntimeException("Redis Error - first attempt")
                }
                // Second attempt succeeds
            }
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.recalculateScores(command)

            // then - should be called twice (1 failure + 1 success)
            verify(exactly = 2) { rankingWriter.replaceAll(any(), any()) }
        }
    }

    @DisplayName("빈 메트릭 처리 테스트")
    @Nested
    inner class EmptyMetricsHandling {

        @DisplayName("메트릭이 없을 때 graceful하게 처리된다")
        @Test
        fun `handles gracefully when no metrics found`() {
            // given
            val command = RecalculateScoresCommand(eventId = "event-empty")
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(currentHour, zoneId)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, zoneId)

            every {
                eventHandledRepository.existsByIdempotencyKey(any())
            } returns false
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns emptyList()
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingReader.exists(any()) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded

            // when
            service.recalculateScores(command)

            // then - replaceAll should not be called when there are no metrics
            verify(exactly = 0) { rankingWriter.replaceAll(any(), any()) }
            // Idempotency key should still be saved
            verify(exactly = 1) { eventHandledRepository.save(any()) }
        }
    }
}
