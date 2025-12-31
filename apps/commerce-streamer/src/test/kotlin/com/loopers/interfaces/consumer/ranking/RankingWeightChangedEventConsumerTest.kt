package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.ranking.RankingWeightRecalculationService
import com.loopers.domain.ranking.RecalculateScoresCommand
import com.loopers.eventschema.CloudEventEnvelope
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
import java.time.Instant

@DisplayName("RankingWeightChangedEventConsumer 테스트")
class RankingWeightChangedEventConsumerTest {

    private lateinit var rankingWeightRecalculationService: RankingWeightRecalculationService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var consumer: RankingWeightChangedEventConsumer
    private lateinit var acknowledgment: Acknowledgment

    @BeforeEach
    fun setUp() {
        rankingWeightRecalculationService = mockk(relaxed = true)
        objectMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        acknowledgment = mockk(relaxed = true)

        consumer = RankingWeightChangedEventConsumer(
            rankingWeightRecalculationService = rankingWeightRecalculationService,
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
            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingWeightRecalculationService.recalculateScores(any()) }
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
            verify(exactly = 0) { rankingWeightRecalculationService.recalculateScores(any()) }
        }
    }

    @Nested
    @DisplayName("서비스 위임 테스트")
    inner class ServiceDelegationTest {

        @Test
        @DisplayName("이벤트 ID를 포함한 커맨드로 서비스를 호출한다")
        fun `calls service with command containing event id`() {
            // given
            val eventId = "test-event-id-123"
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T14:00:00Z"}""",
            )

            val commandSlot = slot<RecalculateScoresCommand>()
            every { rankingWeightRecalculationService.recalculateScores(capture(commandSlot)) } returns Unit

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            assertThat(commandSlot.captured.eventId).isEqualTo(eventId)
        }

        @Test
        @DisplayName("여러 지원 이벤트에 대해 각각 서비스를 호출한다")
        fun `calls service for each supported event`() {
            // given
            val envelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T14:00:00Z"}""",
            )
            val envelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.ranking.weight-changed.v1",
                payload = """{"occurredAt": "2025-01-15T15:00:00Z"}""",
            )

            val records = listOf(
                createConsumerRecord(envelope1),
                createConsumerRecord(envelope2),
            )

            // when
            consumer.consume(records, acknowledgment)

            // then
            verify(exactly = 2) { rankingWeightRecalculationService.recalculateScores(any()) }
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

            val records = listOf(
                createConsumerRecord(supportedEnvelope),
                createConsumerRecord(unsupportedEnvelope),
            )

            // when
            consumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) { rankingWeightRecalculationService.recalculateScores(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        @DisplayName("빈 메시지 목록도 처리하고 acknowledgment를 호출한다")
        fun `handles empty message list and acknowledges`() {
            // when
            consumer.consume(emptyList(), acknowledgment)

            // then
            verify(exactly = 0) { rankingWeightRecalculationService.recalculateScores(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
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
}
