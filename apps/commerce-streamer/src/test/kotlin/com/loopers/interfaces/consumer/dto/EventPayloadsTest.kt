package com.loopers.interfaces.consumer.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("EventPayloads 단위 테스트")
class EventPayloadsTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
            registerModule(JavaTimeModule())
        }
    }

    @DisplayName("CloudEventEnvelope 역직렬화")
    @Nested
    inner class CloudEventEnvelopeDeserialization {

        @DisplayName("JSON을 CloudEventEnvelope로 역직렬화한다")
        @Test
        fun `deserializes JSON to CloudEventEnvelope`() {
            // given
            val json = """
                {
                    "id": "event-123",
                    "type": "loopers.like.created.v1",
                    "source": "commerce-api",
                    "aggregateType": "Like",
                    "aggregateId": "456",
                    "time": "2025-01-15T10:30:00Z",
                    "payload": "{\"userId\":1,\"productId\":100}"
                }
            """.trimIndent()

            // when
            val envelope: CloudEventEnvelope = objectMapper.readValue(json)

            // then
            assertThat(envelope.id).isEqualTo("event-123")
            assertThat(envelope.type).isEqualTo("loopers.like.created.v1")
            assertThat(envelope.source).isEqualTo("commerce-api")
            assertThat(envelope.aggregateType).isEqualTo("Like")
            assertThat(envelope.aggregateId).isEqualTo("456")
            assertThat(envelope.time).isEqualTo(Instant.parse("2025-01-15T10:30:00Z"))
            assertThat(envelope.payload).isEqualTo("{\"userId\":1,\"productId\":100}")
        }

        @DisplayName("모든 필드가 올바르게 매핑된다")
        @Test
        fun `all fields are correctly mapped`() {
            // given
            val time = Instant.now()
            val json = """
                {
                    "id": "uuid-abc",
                    "type": "loopers.order.paid.v1",
                    "source": "order-service",
                    "aggregateType": "Order",
                    "aggregateId": "order-789",
                    "time": "$time",
                    "payload": "{}"
                }
            """.trimIndent()

            // when
            val envelope: CloudEventEnvelope = objectMapper.readValue(json)

            // then
            assertThat(envelope.id).isEqualTo("uuid-abc")
            assertThat(envelope.type).isEqualTo("loopers.order.paid.v1")
            assertThat(envelope.source).isEqualTo("order-service")
            assertThat(envelope.aggregateType).isEqualTo("Order")
            assertThat(envelope.aggregateId).isEqualTo("order-789")
            assertThat(envelope.time).isEqualTo(time)
            assertThat(envelope.payload).isEqualTo("{}")
        }
    }

    @DisplayName("LikeCreatedEventPayload 역직렬화")
    @Nested
    inner class LikeCreatedEventPayloadDeserialization {

        @DisplayName("JSON을 LikeCreatedEventPayload로 역직렬화한다")
        @Test
        fun `deserializes JSON to LikeCreatedEventPayload`() {
            // given
            val json = """
                {
                    "userId": 1,
                    "productId": 100,
                    "occurredAt": "2025-01-15T10:30:00Z"
                }
            """.trimIndent()

            // when
            val payload: LikeCreatedEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.userId).isEqualTo(1L)
            assertThat(payload.productId).isEqualTo(100L)
            assertThat(payload.occurredAt).isEqualTo(Instant.parse("2025-01-15T10:30:00Z"))
        }
    }

    @DisplayName("LikeCanceledEventPayload 역직렬화")
    @Nested
    inner class LikeCanceledEventPayloadDeserialization {

        @DisplayName("JSON을 LikeCanceledEventPayload로 역직렬화한다")
        @Test
        fun `deserializes JSON to LikeCanceledEventPayload`() {
            // given
            val json = """
                {
                    "userId": 2,
                    "productId": 200,
                    "occurredAt": "2025-01-15T11:00:00Z"
                }
            """.trimIndent()

            // when
            val payload: LikeCanceledEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.userId).isEqualTo(2L)
            assertThat(payload.productId).isEqualTo(200L)
            assertThat(payload.occurredAt).isEqualTo(Instant.parse("2025-01-15T11:00:00Z"))
        }
    }

    @DisplayName("OrderPaidEventPayload 역직렬화")
    @Nested
    inner class OrderPaidEventPayloadDeserialization {

        @DisplayName("JSON을 OrderPaidEventPayload로 역직렬화한다")
        @Test
        fun `deserializes JSON to OrderPaidEventPayload`() {
            // given
            val json = """
                {
                    "orderId": 1001,
                    "userId": 5,
                    "totalAmount": 50000,
                    "orderItems": [
                        {"productId": 10, "quantity": 2},
                        {"productId": 20, "quantity": 1}
                    ],
                    "occurredAt": "2025-01-15T12:00:00Z"
                }
            """.trimIndent()

            // when
            val payload: OrderPaidEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.orderId).isEqualTo(1001L)
            assertThat(payload.userId).isEqualTo(5L)
            assertThat(payload.totalAmount).isEqualTo(50000L)
            assertThat(payload.occurredAt).isEqualTo(Instant.parse("2025-01-15T12:00:00Z"))
            assertThat(payload.orderItems).hasSize(2)
        }

        @DisplayName("OrderItemPayload가 올바르게 매핑된다")
        @Test
        fun `orderItems are correctly mapped`() {
            // given
            val json = """
                {
                    "orderId": 1002,
                    "userId": 6,
                    "totalAmount": 30000,
                    "orderItems": [
                        {"productId": 100, "quantity": 3},
                        {"productId": 200, "quantity": 5}
                    ],
                    "occurredAt": "2025-01-15T13:00:00Z"
                }
            """.trimIndent()

            // when
            val payload: OrderPaidEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.orderItems[0].productId).isEqualTo(100L)
            assertThat(payload.orderItems[0].quantity).isEqualTo(3)
            assertThat(payload.orderItems[1].productId).isEqualTo(200L)
            assertThat(payload.orderItems[1].quantity).isEqualTo(5)
        }

        @DisplayName("빈 orderItems 리스트를 처리한다")
        @Test
        fun `handles empty orderItems list`() {
            // given
            val json = """
                {
                    "orderId": 1003,
                    "userId": 7,
                    "totalAmount": 0,
                    "orderItems": [],
                    "occurredAt": "2025-01-15T14:00:00Z"
                }
            """.trimIndent()

            // when
            val payload: OrderPaidEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.orderItems).isEmpty()
        }
    }

    @DisplayName("ProductViewedEventPayload 역직렬화")
    @Nested
    inner class ProductViewedEventPayloadDeserialization {

        @DisplayName("JSON을 ProductViewedEventPayload로 역직렬화한다")
        @Test
        fun `deserializes JSON to ProductViewedEventPayload`() {
            // given
            val json = """
                {
                    "productId": 500,
                    "userId": 10,
                    "occurredAt": "2025-01-15T15:00:00Z"
                }
            """.trimIndent()

            // when
            val payload: ProductViewedEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.productId).isEqualTo(500L)
            assertThat(payload.userId).isEqualTo(10L)
            assertThat(payload.occurredAt).isEqualTo(Instant.parse("2025-01-15T15:00:00Z"))
        }
    }

    @DisplayName("StockDepletedEventPayload 역직렬화")
    @Nested
    inner class StockDepletedEventPayloadDeserialization {

        @DisplayName("JSON을 StockDepletedEventPayload로 역직렬화한다")
        @Test
        fun `deserializes JSON to StockDepletedEventPayload`() {
            // given
            val json = """
                {
                    "productId": 999,
                    "occurredAt": "2025-01-15T16:00:00Z"
                }
            """.trimIndent()

            // when
            val payload: StockDepletedEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.productId).isEqualTo(999L)
            assertThat(payload.occurredAt).isEqualTo(Instant.parse("2025-01-15T16:00:00Z"))
        }
    }
}
