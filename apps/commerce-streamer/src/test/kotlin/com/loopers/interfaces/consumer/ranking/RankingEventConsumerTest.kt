package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.ranking.RankingAggregationService
import com.loopers.domain.ranking.RankingEvent
import com.loopers.domain.ranking.RankingEventType
import com.loopers.eventschema.CloudEventEnvelope
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

@DisplayName("RankingEventConsumer 테스트")
class RankingEventConsumerTest {

    private lateinit var rankingAggregationService: RankingAggregationService
    private lateinit var rankingEventMapper: RankingEventMapper
    private lateinit var eventHandledService: EventHandledService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var consumer: RankingEventConsumer
    private lateinit var acknowledgment: Acknowledgment

    @BeforeEach
    fun setUp() {
        rankingAggregationService = mockk(relaxed = true)
        rankingEventMapper = mockk()
        eventHandledService = mockk(relaxed = true)
        objectMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        acknowledgment = mockk(relaxed = true)

        consumer = RankingEventConsumer(
            rankingAggregationService = rankingAggregationService,
            rankingEventMapper = rankingEventMapper,
            eventHandledService = eventHandledService,
            objectMapper = objectMapper,
        )
    }

    @Nested
    @DisplayName("이벤트 타입 필터링 테스트")
    inner class EventTypeFilteringTest {

        @Test
        @DisplayName("지원하는 이벤트 타입만 처리한다 - loopers.product.viewed.v1")
        fun `processes supported event type - product viewed`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
            )
            val rankingEvent = RankingEvent(
                productId = 100L,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = envelope.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(any()) } returns listOf(rankingEvent)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toRankingEvents(any()) }
            verify(exactly = 1) { rankingAggregationService.add(rankingEvent) }
        }

        @Test
        @DisplayName("지원하는 이벤트 타입만 처리한다 - loopers.like.created.v1")
        fun `processes supported event type - like created`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.created.v1",
                payload = """{"productId": 200, "userId": 1}""",
            )
            val rankingEvent = RankingEvent(
                productId = 200L,
                eventType = RankingEventType.LIKE_CREATED,
                orderAmount = null,
                occurredAt = envelope.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(any()) } returns listOf(rankingEvent)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toRankingEvents(any()) }
            verify(exactly = 1) { rankingAggregationService.add(rankingEvent) }
        }

        @Test
        @DisplayName("지원하는 이벤트 타입만 처리한다 - loopers.like.canceled.v1")
        fun `processes supported event type - like canceled`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.canceled.v1",
                payload = """{"productId": 300, "userId": 1}""",
            )
            val rankingEvent = RankingEvent(
                productId = 300L,
                eventType = RankingEventType.LIKE_CANCELED,
                orderAmount = null,
                occurredAt = envelope.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(any()) } returns listOf(rankingEvent)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toRankingEvents(any()) }
            verify(exactly = 1) { rankingAggregationService.add(rankingEvent) }
        }

        @Test
        @DisplayName("지원하는 이벤트 타입만 처리한다 - loopers.order.paid.v1")
        fun `processes supported event type - order paid`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                payload = """{"orderId": 1, "userId": 1, "totalAmount": 10000, "orderItems": [{"productId": 100, "quantity": 1}]}""",
            )
            val rankingEvent = RankingEvent(
                productId = 100L,
                eventType = RankingEventType.ORDER_PAID,
                orderAmount = BigDecimal("10000"),
                occurredAt = envelope.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(any()) } returns listOf(rankingEvent)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toRankingEvents(any()) }
            verify(exactly = 1) { rankingAggregationService.add(rankingEvent) }
        }

        @Test
        @DisplayName("지원하지 않는 이벤트 타입은 무시한다")
        fun `ignores unsupported event types`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.unknown.event.v1",
                payload = """{"productId": 100}""",
            )
            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 0) { rankingEventMapper.toRankingEvents(any()) }
            verify(exactly = 0) { rankingAggregationService.add(any()) }
            verify(exactly = 0) { eventHandledService.markAsHandled(any()) }
        }

        @Test
        @DisplayName("여러 이벤트 중 지원하는 타입만 처리한다")
        fun `processes only supported types among multiple events`() {
            // given
            val supportedEnvelope = createEnvelope(
                id = "supported-event-id",
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
            )
            val unsupportedEnvelope = createEnvelope(
                id = "unsupported-event-id",
                type = "loopers.unknown.event.v1",
                payload = """{"productId": 200}""",
            )
            val rankingEvent = RankingEvent(
                productId = 100L,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = supportedEnvelope.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(any()) } returns listOf(rankingEvent)

            val records = listOf(
                createConsumerRecord(supportedEnvelope),
                createConsumerRecord(unsupportedEnvelope),
            )

            // when
            consumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toRankingEvents(any()) }
            verify(exactly = 1) { rankingAggregationService.add(any()) }
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
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
            )
            every { eventHandledService.isAlreadyHandled("ranking-aggregation:$eventId") } returns true

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 0) { rankingEventMapper.toRankingEvents(any()) }
            verify(exactly = 0) { rankingAggregationService.add(any()) }
            verify(exactly = 0) { eventHandledService.markAsHandled(any()) }
        }

        @Test
        @DisplayName("새로운 이벤트 처리 후 멱등성 키를 저장한다")
        fun `marks event as handled after processing new event`() {
            // given
            val eventId = "new-event-id"
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
            )
            val rankingEvent = RankingEvent(
                productId = 100L,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = envelope.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(any()) } returns listOf(rankingEvent)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { eventHandledService.markAsHandled("ranking-aggregation:$eventId") }
        }

        @Test
        @DisplayName("멱등성 키 형식이 올바르다 - consumerGroup:eventId")
        fun `idempotency key format is correct`() {
            // given
            val eventId = "test-event-id-123"
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
            )
            val rankingEvent = RankingEvent(
                productId = 100L,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = envelope.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(any()) } returns listOf(rankingEvent)

            val keySlot = slot<String>()
            every { eventHandledService.isAlreadyHandled(capture(keySlot)) } returns false

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            assertThat(keySlot.captured).isEqualTo("ranking-aggregation:$eventId")
        }
    }

    @Nested
    @DisplayName("배치 처리 테스트")
    inner class BatchProcessingTest {

        @Test
        @DisplayName("여러 레코드를 순차적으로 처리한다")
        fun `processes multiple records sequentially`() {
            // given
            val envelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
            )
            val envelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.like.created.v1",
                payload = """{"productId": 200, "userId": 1}""",
            )
            val rankingEvent1 = RankingEvent(
                productId = 100L,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = envelope1.time,
            )
            val rankingEvent2 = RankingEvent(
                productId = 200L,
                eventType = RankingEventType.LIKE_CREATED,
                orderAmount = null,
                occurredAt = envelope2.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(match { it.id == "event-1" }) } returns listOf(rankingEvent1)
            every { rankingEventMapper.toRankingEvents(match { it.id == "event-2" }) } returns listOf(rankingEvent2)

            val records = listOf(
                createConsumerRecord(envelope1),
                createConsumerRecord(envelope2),
            )

            // when
            consumer.consume(records, acknowledgment)

            // then
            verify(exactly = 2) { rankingEventMapper.toRankingEvents(any()) }
            verify(exactly = 2) { rankingAggregationService.add(any()) }
            verify(exactly = 2) { eventHandledService.markAsHandled(any()) }
        }

        @Test
        @DisplayName("ORDER_PAID 이벤트는 여러 RankingEvent를 생성하여 각각 add한다")
        fun `adds each RankingEvent from ORDER_PAID separately`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                payload = """{"orderId": 1, "userId": 1, "totalAmount": 30000, "orderItems": [{"productId": 100, "quantity": 1}, {"productId": 200, "quantity": 1}]}""",
            )
            val rankingEvent1 = RankingEvent(
                productId = 100L,
                eventType = RankingEventType.ORDER_PAID,
                orderAmount = BigDecimal("15000"),
                occurredAt = envelope.time,
            )
            val rankingEvent2 = RankingEvent(
                productId = 200L,
                eventType = RankingEventType.ORDER_PAID,
                orderAmount = BigDecimal("15000"),
                occurredAt = envelope.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(any()) } returns listOf(rankingEvent1, rankingEvent2)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingAggregationService.add(rankingEvent1) }
            verify(exactly = 1) { rankingAggregationService.add(rankingEvent2) }
        }

        @Test
        @DisplayName("처리 완료 후 acknowledgment를 호출한다")
        fun `acknowledges after processing all records`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
            )
            val rankingEvent = RankingEvent(
                productId = 100L,
                eventType = RankingEventType.VIEW,
                orderAmount = null,
                occurredAt = envelope.time,
            )
            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { rankingEventMapper.toRankingEvents(any()) } returns listOf(rankingEvent)

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
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
            aggregateType = "Product",
            aggregateId = "100",
            time = time,
            payload = payload,
        )

    private fun createConsumerRecord(envelope: CloudEventEnvelope): ConsumerRecord<String, String> {
        val json = objectMapper.writeValueAsString(envelope)
        return ConsumerRecord("test-topic", 0, 0L, "key", json)
    }
}
