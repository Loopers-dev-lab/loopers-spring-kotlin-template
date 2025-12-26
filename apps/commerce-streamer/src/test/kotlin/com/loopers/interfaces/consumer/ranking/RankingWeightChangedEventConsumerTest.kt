package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.ranking.CountSnapshot
import com.loopers.domain.ranking.ProductHourlyMetric
import com.loopers.domain.ranking.ProductRankingWriter
import com.loopers.domain.ranking.RankingKeyGenerator
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.domain.ranking.RankingWeight
import com.loopers.domain.ranking.RankingWeightRepository
import com.loopers.domain.ranking.Score
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.ranking.ProductHourlyMetricJpaRepository
import com.loopers.support.idempotency.EventHandledService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@DisplayName("RankingWeightChangedEventConsumer 테스트")
class RankingWeightChangedEventConsumerTest {

    private lateinit var metricJpaRepository: ProductHourlyMetricJpaRepository
    private lateinit var rankingWeightRepository: RankingWeightRepository
    private lateinit var scoreCalculator: RankingScoreCalculator
    private lateinit var rankingWriter: ProductRankingWriter
    private lateinit var eventHandledService: EventHandledService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var consumer: RankingWeightChangedEventConsumer
    private lateinit var acknowledgment: Acknowledgment

    @BeforeEach
    fun setUp() {
        metricJpaRepository = mockk()
        rankingWeightRepository = mockk()
        scoreCalculator = mockk()
        rankingWriter = mockk(relaxed = true)
        eventHandledService = mockk(relaxed = true)
        objectMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        acknowledgment = mockk(relaxed = true)

        consumer = RankingWeightChangedEventConsumer(
            metricJpaRepository = metricJpaRepository,
            rankingWeightRepository = rankingWeightRepository,
            scoreCalculator = scoreCalculator,
            rankingWriter = rankingWriter,
            eventHandledService = eventHandledService,
            objectMapper = objectMapper,
        )
    }

    @Nested
    @DisplayName("이벤트 타입 필터링 테스트")
    inner class EventTypeFilteringTest {

        @Test
        @DisplayName("가중치 변경 이벤트를 처리한다 - loopers.ranking.weight-changed.v1")
        fun `processes weight changed event type`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T14:00:00Z"}""",
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(any()) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns null

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 2) { metricJpaRepository.findAllByStatHour(any()) } // current + previous bucket
            verify(exactly = 1) { eventHandledService.markAsHandled(any()) }
        }

        @Test
        @DisplayName("지원하지 않는 이벤트 타입은 무시한다")
        fun `ignores unsupported event types`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.unknown.event.v1",
                payload = """{"someField": "value"}""",
            )
            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 0) { metricJpaRepository.findAllByStatHour(any()) }
            verify(exactly = 0) { eventHandledService.markAsHandled(any()) }
        }
    }

    @Nested
    @DisplayName("멱등성 테스트")
    inner class IdempotencyTest {

        @Test
        @DisplayName("이미 처리된 이벤트는 무시한다")
        fun `ignores already handled events`() {
            // given
            val eventId = "already-handled-event-id"
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T14:00:00Z"}""",
            )
            every { eventHandledService.isAlreadyHandled("ranking-weight-changed:$eventId") } returns true

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 0) { metricJpaRepository.findAllByStatHour(any()) }
            verify(exactly = 0) { rankingWriter.replaceAll(any(), any()) }
            verify(exactly = 0) { eventHandledService.markAsHandled(any()) }
        }

        @Test
        @DisplayName("새로운 이벤트 처리 후 멱등성 키를 저장한다")
        fun `marks event as handled after processing new event`() {
            // given
            val eventId = "new-event-id"
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T14:00:00Z"}""",
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(any()) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns null

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { eventHandledService.markAsHandled("ranking-weight-changed:$eventId") }
        }

        @Test
        @DisplayName("멱등성 키 형식이 올바르다 - consumerGroup:eventId")
        fun `idempotency key format is correct`() {
            // given
            val eventId = "test-event-id-123"
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T14:00:00Z"}""",
            )

            val keySlot = slot<String>()
            every { eventHandledService.isAlreadyHandled(capture(keySlot)) } returns false
            every { metricJpaRepository.findAllByStatHour(any()) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns null

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            assertThat(keySlot.captured).isEqualTo("ranking-weight-changed:$eventId")
        }
    }

    @Nested
    @DisplayName("점수 재계산 테스트")
    inner class ScoreRecalculationTest {

        @Test
        @DisplayName("가중치 변경 시 모든 상품의 점수를 재계산한다")
        fun `recalculates all product scores on weight change`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = now.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul"))
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val metric1 = createProductHourlyMetric(
                statHour = currentStatHour,
                productId = 100L,
                viewCount = 10,
                likeCount = 5,
                orderCount = 2,
                orderAmount = BigDecimal("1000"),
            )
            val metric2 = createProductHourlyMetric(
                statHour = currentStatHour,
                productId = 200L,
                viewCount = 20,
                likeCount = 10,
                orderCount = 3,
                orderAmount = BigDecimal("2000"),
            )

            val weight = RankingWeight(
                viewWeight = BigDecimal("0.20"),
                likeWeight = BigDecimal("0.30"),
                orderWeight = BigDecimal("0.50"),
            )

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(metric1, metric2)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns weight
            every { scoreCalculator.calculate(any(), any()) } answers {
                val snapshot = firstArg<CountSnapshot>()
                Score.of(snapshot.views * 10.0 + snapshot.likes * 5.0)
            }

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 2) { scoreCalculator.calculate(any(), weight) }
            verify(exactly = 1) {
                rankingWriter.replaceAll(
                    match { it.startsWith("ranking:products:") },
                    match { it.size == 2 && it.containsKey(100L) && it.containsKey(200L) },
                )
            }
        }

        @Test
        @DisplayName("메트릭이 없으면 Redis 업데이트를 스킵한다")
        fun `skips Redis update when no metrics found`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T14:00:00Z"}""",
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(any()) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns null

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 0) { scoreCalculator.calculate(any(), any()) }
            verify(exactly = 0) { rankingWriter.replaceAll(any(), any()) }
        }

        @Test
        @DisplayName("가중치가 없으면 기본값을 사용한다")
        fun `uses fallback weight when no weight found`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val statHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val metric = createProductHourlyMetric(
                statHour = statHour,
                productId = 100L,
                viewCount = 10,
                likeCount = 5,
                orderCount = 2,
                orderAmount = BigDecimal("1000"),
            )

            val weightSlot = slot<RankingWeight>()

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(any()) } returns listOf(metric)
            every { rankingWeightRepository.findLatest() } returns null
            every { scoreCalculator.calculate(any(), capture(weightSlot)) } returns Score.of(100.0)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            val usedWeight = weightSlot.captured
            assertThat(usedWeight.viewWeight).isEqualByComparingTo(BigDecimal("0.10"))
            assertThat(usedWeight.likeWeight).isEqualByComparingTo(BigDecimal("0.20"))
            assertThat(usedWeight.orderWeight).isEqualByComparingTo(BigDecimal("0.60"))
        }

        @Test
        @DisplayName("CountSnapshot이 메트릭에서 올바르게 생성된다")
        fun `creates CountSnapshot correctly from metric`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val statHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val metric = createProductHourlyMetric(
                statHour = statHour,
                productId = 100L,
                viewCount = 15,
                likeCount = 8,
                orderCount = 3,
                orderAmount = BigDecimal("5000.50"),
            )

            val weight = RankingWeight.fallback()
            val snapshotSlot = slot<CountSnapshot>()

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(any()) } returns listOf(metric)
            every { rankingWeightRepository.findLatest() } returns weight
            every { scoreCalculator.calculate(capture(snapshotSlot), any()) } returns Score.of(100.0)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            val snapshot = snapshotSlot.captured
            assertThat(snapshot.views).isEqualTo(15)
            assertThat(snapshot.likes).isEqualTo(8)
            assertThat(snapshot.orderCount).isEqualTo(3)
            assertThat(snapshot.orderAmount).isEqualByComparingTo(BigDecimal("5000.50"))
        }

        @Test
        @DisplayName("현재 시간대 버킷 키를 사용한다")
        fun `uses current bucket key`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = now.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul"))
            val expectedBucketKey = RankingKeyGenerator.currentBucketKey()
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val metric = createProductHourlyMetric(
                statHour = currentStatHour,
                productId = 100L,
            )
            val weight = RankingWeight.fallback()

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(metric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns weight
            every { scoreCalculator.calculate(any(), any()) } returns Score.of(100.0)

            val keySlot = slot<String>()

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingWriter.replaceAll(capture(keySlot), any()) }
            assertThat(keySlot.captured).isEqualTo(expectedBucketKey)
        }
    }

    @Nested
    @DisplayName("이전 버킷 재계산 테스트")
    inner class PreviousBucketRecalculationTest {

        @Test
        @DisplayName("가중치 변경 시 이전 버킷도 재계산한다")
        fun `recalculates previous bucket on weight change`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = now.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul"))
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val currentMetric = createProductHourlyMetric(
                statHour = currentStatHour,
                productId = 100L,
                viewCount = 10,
                likeCount = 5,
                orderCount = 2,
                orderAmount = BigDecimal("1000"),
            )
            val previousMetric = createProductHourlyMetric(
                statHour = previousStatHour,
                productId = 200L,
                viewCount = 20,
                likeCount = 10,
                orderCount = 4,
                orderAmount = BigDecimal("2000"),
            )

            val weight = RankingWeight.fallback()

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(currentMetric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns listOf(previousMetric)
            every { rankingWeightRepository.findLatest() } returns weight
            every { scoreCalculator.calculate(any(), any()) } returns Score.of(100.0)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 2) { rankingWriter.replaceAll(any(), any()) }
        }

        @Test
        @DisplayName("이전 버킷 점수에 감쇠 계수(0.1)가 적용된다")
        fun `applies decay factor to previous bucket scores`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = now.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul"))
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val previousMetric = createProductHourlyMetric(
                statHour = previousStatHour,
                productId = 200L,
                viewCount = 20,
                likeCount = 10,
                orderCount = 4,
                orderAmount = BigDecimal("2000"),
            )

            val weight = RankingWeight.fallback()
            val originalScore = Score.of(1000.0)

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns emptyList()
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns listOf(previousMetric)
            every { rankingWeightRepository.findLatest() } returns weight
            every { scoreCalculator.calculate(any(), any()) } returns originalScore

            val scoresSlot = slot<Map<Long, Score>>()
            val keySlot = slot<String>()

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingWriter.replaceAll(capture(keySlot), capture(scoresSlot)) }
            assertThat(keySlot.captured).isEqualTo(previousBucketKey)
            val decayedScore = scoresSlot.captured[200L]!!
            assertThat(decayedScore.value).isEqualByComparingTo(BigDecimal("100.0"))
        }

        @Test
        @DisplayName("이전 버킷에 메트릭이 없으면 Redis 업데이트를 스킵한다")
        fun `skips previous bucket update when no metrics found`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = now.minus(1, ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul"))
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val currentMetric = createProductHourlyMetric(
                statHour = currentStatHour,
                productId = 100L,
            )

            val weight = RankingWeight.fallback()

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(currentMetric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns weight
            every { scoreCalculator.calculate(any(), any()) } returns Score.of(100.0)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingWriter.replaceAll(any(), any()) }
        }
    }

    @Nested
    @DisplayName("재시도 로직 테스트")
    inner class RetryLogicTest {

        @Test
        @DisplayName("현재 버킷 재계산 실패 시 3회까지 재시도한다")
        fun `retries current bucket recalculation up to 3 times on failure`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val previousHour = now.minus(1, ChronoUnit.HOURS)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul"))
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val metric = createProductHourlyMetric(
                statHour = currentStatHour,
                productId = 100L,
            )

            val weight = RankingWeight.fallback()

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(metric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns weight
            every { scoreCalculator.calculate(any(), any()) } returns Score.of(100.0)
            every { rankingWriter.replaceAll(any(), any()) } throws RuntimeException("Redis error") andThenThrows RuntimeException("Redis error") andThen Unit

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 3) { rankingWriter.replaceAll(any(), any()) }
            verify(exactly = 1) { eventHandledService.markAsHandled(any()) }
        }

        @Test
        @DisplayName("3회 재시도 후에도 실패하면 예외를 던진다")
        fun `throws exception after 3 failed retries`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val previousHour = now.minus(1, ChronoUnit.HOURS)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul"))
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val metric = createProductHourlyMetric(
                statHour = currentStatHour,
                productId = 100L,
            )

            val weight = RankingWeight.fallback()

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(metric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns weight
            every { scoreCalculator.calculate(any(), any()) } returns Score.of(100.0)
            every { rankingWriter.replaceAll(any(), any()) } throws RuntimeException("Redis error")

            val record = createConsumerRecord(envelope)

            // when & then
            org.junit.jupiter.api.assertThrows<RuntimeException> {
                consumer.consume(listOf(record), acknowledgment)
            }
            verify(exactly = 3) { rankingWriter.replaceAll(any(), any()) }
            verify(exactly = 0) { eventHandledService.markAsHandled(any()) }
        }

        @Test
        @DisplayName("이전 버킷 재계산 실패 시 3회까지 재시도한다")
        fun `retries previous bucket recalculation up to 3 times on failure`() {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val currentStatHour = ZonedDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))
            val previousHour = now.minus(1, ChronoUnit.HOURS)
            val previousStatHour = ZonedDateTime.ofInstant(previousHour, ZoneId.of("Asia/Seoul"))
            val currentBucketKey = RankingKeyGenerator.currentBucketKey()
            val previousBucketKey = RankingKeyGenerator.previousBucketKey()
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "$now"}""",
            )

            val currentMetric = createProductHourlyMetric(
                statHour = currentStatHour,
                productId = 100L,
            )
            val previousMetric = createProductHourlyMetric(
                statHour = previousStatHour,
                productId = 200L,
            )

            val weight = RankingWeight.fallback()

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(currentStatHour) } returns listOf(currentMetric)
            every { metricJpaRepository.findAllByStatHour(previousStatHour) } returns listOf(previousMetric)
            every { rankingWeightRepository.findLatest() } returns weight
            every { scoreCalculator.calculate(any(), any()) } returns Score.of(100.0)
            every { rankingWriter.replaceAll(currentBucketKey, any()) } returns Unit
            every { rankingWriter.replaceAll(previousBucketKey, any()) } throws RuntimeException("Redis error") andThenThrows RuntimeException("Redis error") andThen Unit

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingWriter.replaceAll(currentBucketKey, any()) }
            verify(exactly = 3) { rankingWriter.replaceAll(previousBucketKey, any()) }
            verify(exactly = 1) { eventHandledService.markAsHandled(any()) }
        }
    }

    @Nested
    @DisplayName("배치 처리 테스트")
    inner class BatchProcessingTest {

        @Test
        @DisplayName("처리 완료 후 acknowledgment를 호출한다")
        fun `acknowledges after processing all records`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T14:00:00Z"}""",
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(any()) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns null

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        @DisplayName("여러 이벤트 중 지원하는 타입만 처리한다")
        fun `processes only supported types among multiple events`() {
            // given
            val supportedEnvelope = createEnvelope(
                id = "supported-event-id",
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T14:00:00Z"}""",
            )
            val unsupportedEnvelope = createEnvelope(
                id = "unsupported-event-id",
                type = "loopers.unknown.event.v1",
                payload = """{"someField": "value"}""",
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { metricJpaRepository.findAllByStatHour(any()) } returns emptyList()
            every { rankingWeightRepository.findLatest() } returns null

            val records = listOf(
                createConsumerRecord(supportedEnvelope),
                createConsumerRecord(unsupportedEnvelope),
            )

            // when
            consumer.consume(records, acknowledgment)

            // then
            verify(exactly = 2) { metricJpaRepository.findAllByStatHour(any()) } // current + previous bucket
            verify(exactly = 1) { eventHandledService.markAsHandled("ranking-weight-changed:supported-event-id") }
            verify(exactly = 0) { eventHandledService.markAsHandled("ranking-weight-changed:unsupported-event-id") }
        }
    }

    private fun createEnvelope(
        id: String = "evt-${System.nanoTime()}",
        type: String,
        payload: String,
        time: Instant = Instant.now(),
    ): CloudEventEnvelope =
        CloudEventEnvelope(
            id = id,
            type = type,
            source = "commerce-api",
            aggregateType = "RankingWeight",
            aggregateId = "singleton",
            time = time,
            payload = payload,
        )

    private fun createConsumerRecord(envelope: CloudEventEnvelope): ConsumerRecord<String, String> {
        val json = objectMapper.writeValueAsString(envelope)
        return ConsumerRecord("ranking-events", 0, 0L, "key", json)
    }

    private fun createProductHourlyMetric(
        statHour: ZonedDateTime,
        productId: Long,
        viewCount: Long = 0,
        likeCount: Long = 0,
        orderCount: Long = 0,
        orderAmount: BigDecimal = BigDecimal.ZERO,
    ): ProductHourlyMetric =
        ProductHourlyMetric.create(
            statHour = statHour,
            productId = productId,
            viewCount = viewCount,
            likeCount = likeCount,
            orderCount = orderCount,
            orderAmount = orderAmount,
        )
}
