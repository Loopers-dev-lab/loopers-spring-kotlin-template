package com.loopers.domain.ranking

import com.loopers.support.idempotency.EventHandledRepository
import com.loopers.support.idempotency.IdempotencyResult
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

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

    @DisplayName("persistBucket 재시도 테스트")
    @Nested
    inner class PersistBucketRetry {

        @DisplayName("batchAccumulateCounts 실패 시 최대 3회 재시도한다")
        @Test
        fun `retries batchAccumulateCounts up to 3 times on failure`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val command = AccumulateViewMetricCommand(
                eventId = "event-retry-test",
                productId = productId,
                occurredAt = occurredAt,
            )

            every { eventHandledRepository.existsByIdempotencyKey(any()) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } throws RuntimeException("DB Error")

            // when
            service.accumulateViewMetric(command)
            service.flush()

            // then - batchAccumulateCounts should be called 3 times (max retries)
            verify(exactly = 3) { metricRepository.batchAccumulateCounts(any()) }
        }

        @DisplayName("batchAccumulateCounts 첫 번째 시도 성공 시 재시도하지 않는다")
        @Test
        fun `does not retry when batchAccumulateCounts succeeds on first try`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val command = AccumulateViewMetricCommand(
                eventId = "event-success-test",
                productId = productId,
                occurredAt = occurredAt,
            )

            every { eventHandledRepository.existsByIdempotencyKey(any()) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } just Runs

            // when
            service.accumulateViewMetric(command)
            service.flush()

            // then - batchAccumulateCounts should be called exactly once
            verify(exactly = 1) { metricRepository.batchAccumulateCounts(any()) }
        }

        @DisplayName("batchAccumulateCounts 두 번째 시도에서 성공하면 재시도를 중단한다")
        @Test
        fun `stops retrying when batchAccumulateCounts succeeds on second try`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val command = AccumulateViewMetricCommand(
                eventId = "event-retry-success-test",
                productId = productId,
                occurredAt = occurredAt,
            )

            var callCount = 0
            every { eventHandledRepository.existsByIdempotencyKey(any()) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } answers {
                callCount++
                if (callCount == 1) {
                    throw RuntimeException("DB Error - first attempt")
                }
                // Second attempt succeeds
            }

            // when
            service.accumulateViewMetric(command)
            service.flush()

            // then - should be called twice (1 failure + 1 success)
            verify(exactly = 2) { metricRepository.batchAccumulateCounts(any()) }
        }

        @DisplayName("Redis incrementScores는 재시도 없이 호출된다 (멱등 연산)")
        @Test
        fun `incrementScores is called without retry wrapper - idempotent operation`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val command = AccumulateViewMetricCommand(
                eventId = "event-idempotent-test",
                productId = productId,
                occurredAt = occurredAt,
            )

            every { eventHandledRepository.existsByIdempotencyKey(any()) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } just Runs

            // when
            service.accumulateViewMetric(command)
            service.flush()

            // then - incrementScores should be called exactly once (no retry wrapper)
            verify(exactly = 1) { rankingWriter.incrementScores(any(), any()) }
        }
    }

    @DisplayName("transitionBucket 재시도 테스트")
    @Nested
    inner class TransitionBucketRetry {

        @DisplayName("createBucket 실패 시 최대 3회 재시도한다")
        @Test
        fun `retries createBucket up to 3 times on failure`() {
            // given
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()

            every { rankingReader.exists(previousBucketKey) } returns true
            every { rankingReader.exists(RankingKeyGenerator.currentBucketKey()) } returns false
            every { rankingReader.getAllScores(previousBucketKey) } returns mapOf(1L to Score.of(100.0))
            every { rankingWriter.createBucket(any(), any()) } throws RuntimeException("Redis Error")

            // when
            service.transitionBucket()

            // then - createBucket should be called 3 times (max retries)
            verify(exactly = 3) { rankingWriter.createBucket(any(), any()) }
        }

        @DisplayName("createBucket 첫 번째 시도 성공 시 재시도하지 않는다")
        @Test
        fun `does not retry when createBucket succeeds on first try`() {
            // given
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()

            every { rankingReader.exists(previousBucketKey) } returns true
            every { rankingReader.exists(currentBucketKey) } returns false
            every { rankingReader.getAllScores(previousBucketKey) } returns mapOf(1L to Score.of(100.0))
            every { rankingWriter.createBucket(any(), any()) } just Runs

            // when
            service.transitionBucket()

            // then - createBucket should be called exactly once
            verify(exactly = 1) { rankingWriter.createBucket(any(), any()) }
        }

        @DisplayName("createBucket 두 번째 시도에서 성공하면 재시도를 중단한다")
        @Test
        fun `stops retrying when createBucket succeeds on second try`() {
            // given
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()

            var callCount = 0
            every { rankingReader.exists(previousBucketKey) } returns true
            every { rankingReader.exists(currentBucketKey) } returns false
            every { rankingReader.getAllScores(previousBucketKey) } returns mapOf(1L to Score.of(100.0))
            every { rankingWriter.createBucket(any(), any()) } answers {
                callCount++
                if (callCount == 1) {
                    throw RuntimeException("Redis Error - first attempt")
                }
                // Second attempt succeeds
            }

            // when
            service.transitionBucket()

            // then - should be called twice (1 failure + 1 success)
            verify(exactly = 2) { rankingWriter.createBucket(any(), any()) }
        }

        @DisplayName("이전 버킷이 없으면 createBucket을 호출하지 않는다")
        @Test
        fun `does not call createBucket when previous bucket does not exist`() {
            // given
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()

            every { rankingReader.exists(previousBucketKey) } returns false

            // when
            service.transitionBucket()

            // then
            verify(exactly = 0) { rankingWriter.createBucket(any(), any()) }
        }

        @DisplayName("현재 버킷이 이미 존재하면 createBucket을 호출하지 않는다")
        @Test
        fun `does not call createBucket when current bucket already exists`() {
            // given
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()

            every { rankingReader.exists(previousBucketKey) } returns true
            every { rankingReader.exists(currentBucketKey) } returns true

            // when
            service.transitionBucket()

            // then
            verify(exactly = 0) { rankingWriter.createBucket(any(), any()) }
        }
    }

    @DisplayName("재시도 후 예외 전파 테스트")
    @Nested
    inner class RetryExceptionPropagation {

        @DisplayName("batchAccumulateCounts 3회 실패 후 flush에서 예외가 잡힌다 (로깅 후 계속)")
        @Test
        fun `exception from batchAccumulateCounts is caught in flush`() {
            // given
            val productId = 1L
            val occurredAt = Instant.now()
            val command = AccumulateViewMetricCommand(
                eventId = "event-exception-test",
                productId = productId,
                occurredAt = occurredAt,
            )

            every { eventHandledRepository.existsByIdempotencyKey(any()) } returns false
            every { eventHandledRepository.save(any()) } returns IdempotencyResult.Recorded
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } throws RuntimeException("DB Error")

            // when & then - flush catches the exception internally, so no exception propagates
            service.accumulateViewMetric(command)
            service.flush() // Should not throw - exception is caught and logged
        }

        @DisplayName("createBucket 3회 실패 후 transitionBucket에서 예외가 잡힌다 (로깅 후 계속)")
        @Test
        fun `exception from createBucket is caught in transitionBucket`() {
            // given
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()

            every { rankingReader.exists(previousBucketKey) } returns true
            every { rankingReader.exists(RankingKeyGenerator.currentBucketKey()) } returns false
            every { rankingReader.getAllScores(previousBucketKey) } returns mapOf(1L to Score.of(100.0))
            every { rankingWriter.createBucket(any(), any()) } throws RuntimeException("Redis Error")

            // when & then - transitionBucket catches the exception internally
            service.transitionBucket() // Should not throw - exception is caught and logged
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

    @DisplayName("onShutdown 테스트")
    @Nested
    inner class OnShutdownTest {

        @DisplayName("onShutdown 호출 시 flush가 호출된다")
        @Test
        fun `onShutdown calls flush`() {
            // given - buffer is empty, so flush should just return early
            // when
            service.onShutdown()

            // then - no exception, flush completed (empty buffer case)
        }
    }
}
