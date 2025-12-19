package com.loopers.interfaces.consumer.product.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("New Event Payloads (Simplified) Unit Tests")
class NewEventPayloadsTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
        }
    }

    @DisplayName("LikeEventPayload deserialization")
    @Nested
    inner class LikeEventPayloadDeserialization {

        @DisplayName("deserializes JSON to LikeEventPayload")
        @Test
        fun `deserializes JSON to LikeEventPayload`() {
            // given
            val json = """
                {
                    "productId": 100,
                    "userId": 1
                }
            """.trimIndent()

            // when
            val payload: LikeEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.productId).isEqualTo(100L)
            assertThat(payload.userId).isEqualTo(1L)
        }

        @DisplayName("ignores extra fields in JSON")
        @Test
        fun `ignores extra fields in JSON`() {
            // given
            val json = """
                {
                    "productId": 200,
                    "userId": 2,
                    "occurredAt": "2025-01-15T10:30:00Z",
                    "extraField": "ignored"
                }
            """.trimIndent()

            // when
            val payload: LikeEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.productId).isEqualTo(200L)
            assertThat(payload.userId).isEqualTo(2L)
        }
    }

    @DisplayName("OrderPaidEventPayload deserialization")
    @Nested
    inner class OrderPaidEventPayloadDeserialization {

        @DisplayName("deserializes JSON to OrderPaidEventPayload")
        @Test
        fun `deserializes JSON to OrderPaidEventPayload`() {
            // given
            val json = """
                {
                    "orderId": 1001,
                    "orderItems": [
                        {"productId": 10, "quantity": 2},
                        {"productId": 20, "quantity": 1}
                    ]
                }
            """.trimIndent()

            // when
            val payload: OrderPaidEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.orderId).isEqualTo(1001L)
            assertThat(payload.orderItems).hasSize(2)
        }

        @DisplayName("OrderItem is correctly mapped")
        @Test
        fun `orderItems are correctly mapped`() {
            // given
            val json = """
                {
                    "orderId": 1002,
                    "orderItems": [
                        {"productId": 100, "quantity": 3},
                        {"productId": 200, "quantity": 5}
                    ]
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

        @DisplayName("handles empty orderItems list")
        @Test
        fun `handles empty orderItems list`() {
            // given
            val json = """
                {
                    "orderId": 1003,
                    "orderItems": []
                }
            """.trimIndent()

            // when
            val payload: OrderPaidEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.orderItems).isEmpty()
        }

        @DisplayName("ignores extra fields in JSON")
        @Test
        fun `ignores extra fields in JSON`() {
            // given
            val json = """
                {
                    "orderId": 1004,
                    "userId": 5,
                    "totalAmount": 50000,
                    "orderItems": [{"productId": 10, "quantity": 1}],
                    "occurredAt": "2025-01-15T12:00:00Z"
                }
            """.trimIndent()

            // when
            val payload: OrderPaidEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.orderId).isEqualTo(1004L)
            assertThat(payload.orderItems).hasSize(1)
        }
    }

    @DisplayName("ProductViewedEventPayload deserialization")
    @Nested
    inner class ProductViewedEventPayloadDeserialization {

        @DisplayName("deserializes JSON to ProductViewedEventPayload")
        @Test
        fun `deserializes JSON to ProductViewedEventPayload`() {
            // given
            val json = """
                {
                    "productId": 500,
                    "userId": 10
                }
            """.trimIndent()

            // when
            val payload: ProductViewedEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.productId).isEqualTo(500L)
            assertThat(payload.userId).isEqualTo(10L)
        }

        @DisplayName("ignores extra fields in JSON")
        @Test
        fun `ignores extra fields in JSON`() {
            // given
            val json = """
                {
                    "productId": 600,
                    "userId": 20,
                    "occurredAt": "2025-01-15T15:00:00Z"
                }
            """.trimIndent()

            // when
            val payload: ProductViewedEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.productId).isEqualTo(600L)
            assertThat(payload.userId).isEqualTo(20L)
        }
    }

    @DisplayName("StockDepletedEventPayload deserialization")
    @Nested
    inner class StockDepletedEventPayloadDeserialization {

        @DisplayName("deserializes JSON to StockDepletedEventPayload")
        @Test
        fun `deserializes JSON to StockDepletedEventPayload`() {
            // given
            val json = """
                {
                    "productId": 999
                }
            """.trimIndent()

            // when
            val payload: StockDepletedEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.productId).isEqualTo(999L)
        }

        @DisplayName("ignores extra fields in JSON")
        @Test
        fun `ignores extra fields in JSON`() {
            // given
            val json = """
                {
                    "productId": 888,
                    "occurredAt": "2025-01-15T16:00:00Z"
                }
            """.trimIndent()

            // when
            val payload: StockDepletedEventPayload = objectMapper.readValue(json)

            // then
            assertThat(payload.productId).isEqualTo(888L)
        }
    }
}
