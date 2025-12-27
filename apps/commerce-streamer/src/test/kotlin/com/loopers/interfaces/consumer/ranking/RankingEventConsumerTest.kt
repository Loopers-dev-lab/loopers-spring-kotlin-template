package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.ranking.AccumulateLikeCanceledMetricCommand
import com.loopers.domain.ranking.AccumulateLikeCreatedMetricCommand
import com.loopers.domain.ranking.AccumulateOrderPaidMetricCommand
import com.loopers.domain.ranking.AccumulateViewMetricCommand
import com.loopers.domain.ranking.RankingAggregationService
import com.loopers.eventschema.CloudEventEnvelope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
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
    private lateinit var objectMapper: ObjectMapper
    private lateinit var consumer: RankingEventConsumer
    private lateinit var acknowledgment: Acknowledgment

    @BeforeEach
    fun setUp() {
        rankingAggregationService = mockk(relaxed = true)
        rankingEventMapper = mockk()
        objectMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        acknowledgment = mockk(relaxed = true)

        consumer = RankingEventConsumer(
            rankingAggregationService = rankingAggregationService,
            rankingEventMapper = rankingEventMapper,
            objectMapper = objectMapper,
        )
    }

    @Nested
    @DisplayName("이벤트 타입별 서비스 위임 테스트")
    inner class EventTypeDelegationTest {

        @Test
        @DisplayName("loopers.product.viewed.v1 이벤트를 accumulateViewMetric으로 위임한다")
        fun `delegates product viewed event to accumulateViewMetric`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
            )
            val command = AccumulateViewMetricCommand(
                eventId = envelope.id,
                productId = 100L,
                occurredAt = envelope.time,
            )
            every { rankingEventMapper.toViewCommand(any()) } returns command

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toViewCommand(any()) }
            verify(exactly = 1) { rankingAggregationService.accumulateViewMetric(command) }
        }

        @Test
        @DisplayName("loopers.like.created.v1 이벤트를 accumulateLikeCreatedMetric으로 위임한다")
        fun `delegates like created event to accumulateLikeCreatedMetric`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.created.v1",
                payload = """{"productId": 200, "userId": 1}""",
            )
            val command = AccumulateLikeCreatedMetricCommand(
                eventId = envelope.id,
                productId = 200L,
                occurredAt = envelope.time,
            )
            every { rankingEventMapper.toLikeCreatedCommand(any()) } returns command

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toLikeCreatedCommand(any()) }
            verify(exactly = 1) { rankingAggregationService.accumulateLikeCreatedMetric(command) }
        }

        @Test
        @DisplayName("loopers.like.canceled.v1 이벤트를 accumulateLikeCanceledMetric으로 위임한다")
        fun `delegates like canceled event to accumulateLikeCanceledMetric`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.canceled.v1",
                payload = """{"productId": 300, "userId": 1}""",
            )
            val command = AccumulateLikeCanceledMetricCommand(
                eventId = envelope.id,
                productId = 300L,
                occurredAt = envelope.time,
            )
            every { rankingEventMapper.toLikeCanceledCommand(any()) } returns command

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toLikeCanceledCommand(any()) }
            verify(exactly = 1) { rankingAggregationService.accumulateLikeCanceledMetric(command) }
        }

        @Test
        @DisplayName("loopers.order.paid.v1 이벤트를 accumulateOrderPaidMetric으로 위임한다")
        fun `delegates order paid event to accumulateOrderPaidMetric`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                payload = """{"orderId": 1, "userId": 1, "totalAmount": 10000, "orderItems": [{"productId": 100, "unitPrice": 10000, "quantity": 1}]}""",
            )
            val command = AccumulateOrderPaidMetricCommand(
                eventId = envelope.id,
                items = listOf(
                    AccumulateOrderPaidMetricCommand.Item(
                        productId = 100L,
                        orderAmount = BigDecimal("10000"),
                    ),
                ),
                occurredAt = envelope.time,
            )
            every { rankingEventMapper.toOrderPaidCommand(any()) } returns command

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toOrderPaidCommand(any()) }
            verify(exactly = 1) { rankingAggregationService.accumulateOrderPaidMetric(command) }
        }
    }

    @Nested
    @DisplayName("이벤트 타입 필터링 테스트")
    inner class EventTypeFilteringTest {

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
            verify(exactly = 0) { rankingEventMapper.toViewCommand(any()) }
            verify(exactly = 0) { rankingEventMapper.toLikeCreatedCommand(any()) }
            verify(exactly = 0) { rankingEventMapper.toLikeCanceledCommand(any()) }
            verify(exactly = 0) { rankingEventMapper.toOrderPaidCommand(any()) }
            verify(exactly = 0) { rankingAggregationService.accumulateViewMetric(any()) }
            verify(exactly = 0) { rankingAggregationService.accumulateLikeCreatedMetric(any()) }
            verify(exactly = 0) { rankingAggregationService.accumulateLikeCanceledMetric(any()) }
            verify(exactly = 0) { rankingAggregationService.accumulateOrderPaidMetric(any()) }
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
            val command = AccumulateViewMetricCommand(
                eventId = supportedEnvelope.id,
                productId = 100L,
                occurredAt = supportedEnvelope.time,
            )
            every { rankingEventMapper.toViewCommand(any()) } returns command

            val records = listOf(
                createConsumerRecord(supportedEnvelope),
                createConsumerRecord(unsupportedEnvelope),
            )

            // when
            consumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toViewCommand(any()) }
            verify(exactly = 1) { rankingAggregationService.accumulateViewMetric(any()) }
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
            val viewCommand = AccumulateViewMetricCommand(
                eventId = envelope1.id,
                productId = 100L,
                occurredAt = envelope1.time,
            )
            val likeCommand = AccumulateLikeCreatedMetricCommand(
                eventId = envelope2.id,
                productId = 200L,
                occurredAt = envelope2.time,
            )
            every { rankingEventMapper.toViewCommand(any()) } returns viewCommand
            every { rankingEventMapper.toLikeCreatedCommand(any()) } returns likeCommand

            val records = listOf(
                createConsumerRecord(envelope1),
                createConsumerRecord(envelope2),
            )

            // when
            consumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) { rankingEventMapper.toViewCommand(any()) }
            verify(exactly = 1) { rankingEventMapper.toLikeCreatedCommand(any()) }
            verify(exactly = 1) { rankingAggregationService.accumulateViewMetric(any()) }
            verify(exactly = 1) { rankingAggregationService.accumulateLikeCreatedMetric(any()) }
        }

        @Test
        @DisplayName("처리 완료 후 acknowledgment를 호출한다")
        fun `acknowledges after processing all records`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
            )
            val command = AccumulateViewMetricCommand(
                eventId = envelope.id,
                productId = 100L,
                occurredAt = envelope.time,
            )
            every { rankingEventMapper.toViewCommand(any()) } returns command

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
