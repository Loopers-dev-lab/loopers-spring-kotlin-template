package com.loopers.domain.ranking

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("RankingAggregationService 단위 테스트")
class RankingAggregationServiceTest {

    private lateinit var metricRepository: ProductHourlyMetricRepository
    private lateinit var rankingWriter: ProductRankingWriter
    private lateinit var rankingReader: ProductRankingReader
    private lateinit var rankingWeightRepository: RankingWeightRepository
    private lateinit var scoreCalculator: RankingScoreCalculator
    private lateinit var service: RankingAggregationService

    @BeforeEach
    fun setUp() {
        metricRepository = mockk()
        rankingWriter = mockk(relaxed = true)
        rankingReader = mockk()
        rankingWeightRepository = mockk()
        scoreCalculator = RankingScoreCalculator()

        service = RankingAggregationService(
            metricRepository = metricRepository,
            rankingWriter = rankingWriter,
            rankingReader = rankingReader,
            rankingWeightRepository = rankingWeightRepository,
            scoreCalculator = scoreCalculator,
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
            val command = AccumulateMetricCommand(
                items = listOf(
                    AccumulateMetricCommand.Item(
                        productId = productId,
                        metricType = MetricType.VIEW,
                        orderAmount = null,
                        occurredAt = occurredAt,
                    ),
                ),
            )

            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } throws RuntimeException("DB Error")

            // when
            service.accumulateMetric(command)
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
            val command = AccumulateMetricCommand(
                items = listOf(
                    AccumulateMetricCommand.Item(
                        productId = productId,
                        metricType = MetricType.VIEW,
                        orderAmount = null,
                        occurredAt = occurredAt,
                    ),
                ),
            )

            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } just Runs

            // when
            service.accumulateMetric(command)
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
            val command = AccumulateMetricCommand(
                items = listOf(
                    AccumulateMetricCommand.Item(
                        productId = productId,
                        metricType = MetricType.VIEW,
                        orderAmount = null,
                        occurredAt = occurredAt,
                    ),
                ),
            )

            var callCount = 0
            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } answers {
                callCount++
                if (callCount == 1) {
                    throw RuntimeException("DB Error - first attempt")
                }
                // Second attempt succeeds
            }

            // when
            service.accumulateMetric(command)
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
            val command = AccumulateMetricCommand(
                items = listOf(
                    AccumulateMetricCommand.Item(
                        productId = productId,
                        metricType = MetricType.VIEW,
                        orderAmount = null,
                        occurredAt = occurredAt,
                    ),
                ),
            )

            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } just Runs

            // when
            service.accumulateMetric(command)
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
            val command = AccumulateMetricCommand(
                items = listOf(
                    AccumulateMetricCommand.Item(
                        productId = productId,
                        metricType = MetricType.VIEW,
                        orderAmount = null,
                        occurredAt = occurredAt,
                    ),
                ),
            )

            every { rankingWeightRepository.findLatest() } returns RankingWeight.fallback()
            every { metricRepository.batchAccumulateCounts(any()) } throws RuntimeException("DB Error")

            // when & then - flush catches the exception internally, so no exception propagates
            service.accumulateMetric(command)
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
}
