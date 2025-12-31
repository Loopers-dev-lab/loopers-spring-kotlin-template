package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.ranking.AccumulateMetricsCommand
import com.loopers.domain.ranking.RankingAggregationService
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
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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
        objectMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        rankingEventMapper = RankingEventMapper(objectMapper)
        acknowledgment = mockk(relaxed = true)

        consumer = RankingEventConsumer(
            rankingAggregationService = rankingAggregationService,
            rankingEventMapper = rankingEventMapper,
            objectMapper = objectMapper,
        )
    }

    @Nested
    @DisplayName("배치 처리 테스트")
    inner class BatchProcessingTest {

        @Test
        @DisplayName("여러 레코드를 하나의 배치 커맨드로 처리한다")
        fun `processes multiple records as single batch command`() {
            // given
            val envelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100}""",
            )
            val envelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.like.created.v1",
                payload = """{"productId": 200}""",
            )

            val records = listOf(
                createConsumerRecord(envelope1),
                createConsumerRecord(envelope2),
            )

            val commandSlot = slot<AccumulateMetricsCommand>()
            every { rankingAggregationService.accumulateMetrics(capture(commandSlot)) } returns Unit

            // when
            consumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) { rankingAggregationService.accumulateMetrics(any()) }
            assertThat(commandSlot.captured.items).hasSize(2)
        }

        @Test
        @DisplayName("처리 완료 후 acknowledgment를 호출한다")
        fun `acknowledges after processing all records`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100}""",
            )

            val record = createConsumerRecord(envelope)

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        @DisplayName("빈 배치는 서비스를 호출하지 않고 ack만 한다")
        fun `empty batch acknowledges without calling service`() {
            // given
            val records = emptyList<ConsumerRecord<String, String>>()

            // when
            consumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { rankingAggregationService.accumulateMetrics(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @Nested
    @DisplayName("이벤트 타입별 변환 테스트")
    inner class EventTypeConversionTest {

        @Test
        @DisplayName("VIEW 이벤트를 viewDelta=1인 Item으로 변환한다")
        fun `converts VIEW event to Item with viewDelta=1`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100}""",
            )
            val record = createConsumerRecord(envelope)

            val commandSlot = slot<AccumulateMetricsCommand>()
            every { rankingAggregationService.accumulateMetrics(capture(commandSlot)) } returns Unit

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            val item = commandSlot.captured.items.first()
            assertThat(item.productId).isEqualTo(100L)
            assertThat(item.viewDelta).isEqualTo(1)
            assertThat(item.likeCreatedDelta).isEqualTo(0)
            assertThat(item.likeCanceledDelta).isEqualTo(0)
            assertThat(item.orderCountDelta).isEqualTo(0)
            assertThat(item.orderAmountDelta).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        @DisplayName("LIKE_CREATED 이벤트를 likeCreatedDelta=1인 Item으로 변환한다")
        fun `converts LIKE_CREATED event to Item with likeCreatedDelta=1`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.created.v1",
                payload = """{"productId": 200}""",
            )
            val record = createConsumerRecord(envelope)

            val commandSlot = slot<AccumulateMetricsCommand>()
            every { rankingAggregationService.accumulateMetrics(capture(commandSlot)) } returns Unit

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            val item = commandSlot.captured.items.first()
            assertThat(item.productId).isEqualTo(200L)
            assertThat(item.viewDelta).isEqualTo(0)
            assertThat(item.likeCreatedDelta).isEqualTo(1)
            assertThat(item.likeCanceledDelta).isEqualTo(0)
        }

        @Test
        @DisplayName("LIKE_CANCELED 이벤트를 likeCanceledDelta=1인 Item으로 변환한다")
        fun `converts LIKE_CANCELED event to Item with likeCanceledDelta=1`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.canceled.v1",
                payload = """{"productId": 300}""",
            )
            val record = createConsumerRecord(envelope)

            val commandSlot = slot<AccumulateMetricsCommand>()
            every { rankingAggregationService.accumulateMetrics(capture(commandSlot)) } returns Unit

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            val item = commandSlot.captured.items.first()
            assertThat(item.productId).isEqualTo(300L)
            assertThat(item.viewDelta).isEqualTo(0)
            assertThat(item.likeCreatedDelta).isEqualTo(0)
            assertThat(item.likeCanceledDelta).isEqualTo(1)
        }

        @Test
        @DisplayName("ORDER_PAID 이벤트를 상품별 Item으로 변환한다")
        fun `converts ORDER_PAID event to per-product Items`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                payload = """{"totalAmount": 30000, "orderItems": [{"productId": 100, "quantity": 2, "unitPrice": 10000}, {"productId": 200, "quantity": 1, "unitPrice": 10000}]}""",
            )
            val record = createConsumerRecord(envelope)

            val commandSlot = slot<AccumulateMetricsCommand>()
            every { rankingAggregationService.accumulateMetrics(capture(commandSlot)) } returns Unit

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            assertThat(commandSlot.captured.items).hasSize(2)

            val item1 = commandSlot.captured.items[0]
            assertThat(item1.productId).isEqualTo(100L)
            assertThat(item1.orderCountDelta).isEqualTo(1)
            assertThat(item1.orderAmountDelta).isEqualByComparingTo(BigDecimal("20000")) // 10000 * 2

            val item2 = commandSlot.captured.items[1]
            assertThat(item2.productId).isEqualTo(200L)
            assertThat(item2.orderCountDelta).isEqualTo(1)
            assertThat(item2.orderAmountDelta).isEqualByComparingTo(BigDecimal("10000")) // 10000 * 1
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
            verify(exactly = 0) { rankingAggregationService.accumulateMetrics(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        @DisplayName("여러 이벤트 중 지원하는 타입만 처리한다")
        fun `processes only supported types among multiple events`() {
            // given
            val supportedEnvelope = createEnvelope(
                id = "supported-event-id",
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100}""",
            )
            val unsupportedEnvelope = createEnvelope(
                id = "unsupported-event-id",
                type = "loopers.unknown.event.v1",
                payload = """{"productId": 200}""",
            )

            val records = listOf(
                createConsumerRecord(supportedEnvelope),
                createConsumerRecord(unsupportedEnvelope),
            )

            val commandSlot = slot<AccumulateMetricsCommand>()
            every { rankingAggregationService.accumulateMetrics(capture(commandSlot)) } returns Unit

            // when
            consumer.consume(records, acknowledgment)

            // then
            assertThat(commandSlot.captured.items).hasSize(1)
            assertThat(commandSlot.captured.items[0].productId).isEqualTo(100L)
        }
    }

    @Nested
    @DisplayName("statHour 변환 테스트")
    inner class StatHourConversionTest {

        @Test
        @DisplayName("이벤트 시간을 Asia/Seoul 타임존으로 변환한다")
        fun `converts event time to Asia Seoul timezone`() {
            // given
            val eventTime = Instant.parse("2025-01-01T00:30:00Z") // UTC
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100}""",
                time = eventTime,
            )
            val record = createConsumerRecord(envelope)

            val commandSlot = slot<AccumulateMetricsCommand>()
            every { rankingAggregationService.accumulateMetrics(capture(commandSlot)) } returns Unit

            // when
            consumer.consume(listOf(record), acknowledgment)

            // then
            val item = commandSlot.captured.items.first()
            val expectedStatHour = ZonedDateTime.ofInstant(eventTime, ZoneId.of("Asia/Seoul"))
            assertThat(item.statHour).isEqualTo(expectedStatHour)
        }
    }

    @Nested
    @DisplayName("에러 처리 테스트")
    inner class ErrorHandlingTest {

        @Test
        @DisplayName("파싱 실패한 레코드는 건너뛰고 나머지를 처리한다")
        fun `skips failed records and processes remaining`() {
            // given
            val validEnvelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100}""",
            )
            val invalidRecord = ConsumerRecord("test-topic", 0, 0L, "key", "invalid-json")
            val validRecord = createConsumerRecord(validEnvelope)

            val commandSlot = slot<AccumulateMetricsCommand>()
            every { rankingAggregationService.accumulateMetrics(capture(commandSlot)) } returns Unit

            // when
            consumer.consume(listOf(invalidRecord, validRecord), acknowledgment)

            // then
            assertThat(commandSlot.captured.items).hasSize(1)
            assertThat(commandSlot.captured.items[0].productId).isEqualTo(100L)
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
