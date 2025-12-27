package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.ranking.AccumulateLikeCanceledMetricCommand
import com.loopers.domain.ranking.AccumulateLikeCreatedMetricCommand
import com.loopers.domain.ranking.AccumulateOrderPaidMetricCommand
import com.loopers.domain.ranking.AccumulateViewMetricCommand
import com.loopers.eventschema.CloudEventEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

@DisplayName("RankingEventMapper 테스트")
class RankingEventMapperTest {
    private lateinit var objectMapper: ObjectMapper
    private lateinit var rankingEventMapper: RankingEventMapper

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // JacksonConfig와 동일한 설정 - 알 수 없는 필드 무시
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
        rankingEventMapper = RankingEventMapper(objectMapper)
    }

    @Nested
    @DisplayName("toViewCommand - VIEW 이벤트를 AccumulateViewMetricCommand로 변환")
    inner class ToViewCommandTest {
        @Test
        @DisplayName("product.viewed 이벤트를 AccumulateViewMetricCommand로 매핑한다")
        fun `maps product viewed event to AccumulateViewMetricCommand`() {
            // given
            val eventId = "view-event-123"
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.product.viewed.v1",
                aggregateId = "100",
                payload = """{"productId": 100, "userId": 1}""",
                time = eventTime,
            )

            // when
            val command = rankingEventMapper.toViewCommand(envelope)

            // then
            assertThat(command).isInstanceOf(AccumulateViewMetricCommand::class.java)
            assertThat(command.eventId).isEqualTo(eventId)
            assertThat(command.productId).isEqualTo(100L)
            assertThat(command.occurredAt).isEqualTo(eventTime)
        }

        @Test
        @DisplayName("eventId는 envelope.id에서 추출된다")
        fun `eventId is extracted from envelope id`() {
            // given
            val eventId = "unique-event-id-456"
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.product.viewed.v1",
                aggregateId = "100",
                payload = """{"productId": 100, "userId": 1}""",
            )

            // when
            val command = rankingEventMapper.toViewCommand(envelope)

            // then
            assertThat(command.eventId).isEqualTo(eventId)
        }
    }

    @Nested
    @DisplayName("toLikeCreatedCommand - LIKE_CREATED 이벤트를 AccumulateLikeCreatedMetricCommand로 변환")
    inner class ToLikeCreatedCommandTest {
        @Test
        @DisplayName("like.created 이벤트를 AccumulateLikeCreatedMetricCommand로 매핑한다")
        fun `maps like created event to AccumulateLikeCreatedMetricCommand`() {
            // given
            val eventId = "like-created-event-123"
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.like.created.v1",
                aggregateId = "200",
                payload = """{"productId": 200, "userId": 1}""",
                time = eventTime,
            )

            // when
            val command = rankingEventMapper.toLikeCreatedCommand(envelope)

            // then
            assertThat(command).isInstanceOf(AccumulateLikeCreatedMetricCommand::class.java)
            assertThat(command.eventId).isEqualTo(eventId)
            assertThat(command.productId).isEqualTo(200L)
            assertThat(command.occurredAt).isEqualTo(eventTime)
        }
    }

    @Nested
    @DisplayName("toLikeCanceledCommand - LIKE_CANCELED 이벤트를 AccumulateLikeCanceledMetricCommand로 변환")
    inner class ToLikeCanceledCommandTest {
        @Test
        @DisplayName("like.canceled 이벤트를 AccumulateLikeCanceledMetricCommand로 매핑한다")
        fun `maps like canceled event to AccumulateLikeCanceledMetricCommand`() {
            // given
            val eventId = "like-canceled-event-123"
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.like.canceled.v1",
                aggregateId = "300",
                payload = """{"productId": 300, "userId": 1}""",
                time = eventTime,
            )

            // when
            val command = rankingEventMapper.toLikeCanceledCommand(envelope)

            // then
            assertThat(command).isInstanceOf(AccumulateLikeCanceledMetricCommand::class.java)
            assertThat(command.eventId).isEqualTo(eventId)
            assertThat(command.productId).isEqualTo(300L)
            assertThat(command.occurredAt).isEqualTo(eventTime)
        }
    }

    @Nested
    @DisplayName("toOrderPaidCommand - ORDER_PAID 이벤트를 AccumulateOrderPaidMetricCommand로 변환")
    inner class ToOrderPaidCommandTest {
        @Test
        @DisplayName("order.paid 이벤트를 AccumulateOrderPaidMetricCommand로 매핑한다 - unitPrice * quantity로 계산")
        fun `maps order paid event to AccumulateOrderPaidMetricCommand with per-item calculation`() {
            // given
            val eventId = "order-paid-event-123"
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.order.paid.v1",
                aggregateId = "order-1",
                payload = """{"orderId": 1, "userId": 1, "totalAmount": 30000, "orderItems": [{"productId": 100, "quantity": 2, "unitPrice": 10000}, {"productId": 200, "quantity": 1, "unitPrice": 10000}]}""",
                time = eventTime,
            )

            // when
            val command = rankingEventMapper.toOrderPaidCommand(envelope)

            // then
            assertThat(command).isInstanceOf(AccumulateOrderPaidMetricCommand::class.java)
            assertThat(command.eventId).isEqualTo(eventId)
            assertThat(command.occurredAt).isEqualTo(eventTime)
            assertThat(command.items).hasSize(2)

            // 상품 100: unitPrice 10000 * quantity 2 = 20000
            assertThat(command.items[0].productId).isEqualTo(100L)
            assertThat(command.items[0].orderAmount).isEqualByComparingTo(BigDecimal("20000"))

            // 상품 200: unitPrice 10000 * quantity 1 = 10000
            assertThat(command.items[1].productId).isEqualTo(200L)
            assertThat(command.items[1].orderAmount).isEqualByComparingTo(BigDecimal("10000"))
        }

        @Test
        @DisplayName("빈 orderItems 주문 시 빈 items 리스트를 가진 Command를 반환한다")
        fun `returns Command with empty items for order with no items`() {
            // given
            val eventId = "order-paid-empty-123"
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.order.paid.v1",
                aggregateId = "order-3",
                payload = """{"orderId": 3, "userId": 1, "totalAmount": 0, "orderItems": []}""",
            )

            // when
            val command = rankingEventMapper.toOrderPaidCommand(envelope)

            // then
            assertThat(command.eventId).isEqualTo(eventId)
            assertThat(command.items).isEmpty()
        }

        @Test
        @DisplayName("여러 상품의 다양한 quantity와 unitPrice가 각각 정확하게 계산된다")
        fun `multiple items with various quantities and prices are calculated correctly`() {
            // given
            val eventId = "order-paid-multi-123"
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.order.paid.v1",
                aggregateId = "order-multi",
                payload = """{"orderId": 5, "userId": 1, "totalAmount": 125000, "orderItems": [{"productId": 100, "quantity": 3, "unitPrice": 25000}, {"productId": 200, "quantity": 2, "unitPrice": 15000}, {"productId": 300, "quantity": 1, "unitPrice": 20000}]}""",
                time = eventTime,
            )

            // when
            val command = rankingEventMapper.toOrderPaidCommand(envelope)

            // then
            assertThat(command.items).hasSize(3)
            // 상품 100: 25000 * 3 = 75000
            assertThat(command.items[0].productId).isEqualTo(100L)
            assertThat(command.items[0].orderAmount).isEqualByComparingTo(BigDecimal("75000"))
            // 상품 200: 15000 * 2 = 30000
            assertThat(command.items[1].productId).isEqualTo(200L)
            assertThat(command.items[1].orderAmount).isEqualByComparingTo(BigDecimal("30000"))
            // 상품 300: 20000 * 1 = 20000
            assertThat(command.items[2].productId).isEqualTo(300L)
            assertThat(command.items[2].orderAmount).isEqualByComparingTo(BigDecimal("20000"))
        }
    }

    private fun createEnvelope(
        id: String = "evt-${System.nanoTime()}",
        type: String,
        aggregateId: String,
        payload: String,
        time: Instant = Instant.now(),
    ): CloudEventEnvelope =
        CloudEventEnvelope(
            id = id,
            type = type,
            source = "commerce-api",
            aggregateType = "Product",
            aggregateId = aggregateId,
            time = time,
            payload = payload,
        )
}
