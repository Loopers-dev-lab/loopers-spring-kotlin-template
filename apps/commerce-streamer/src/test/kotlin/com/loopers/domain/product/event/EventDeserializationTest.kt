package com.loopers.domain.product.event

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Domain Event Deserialization Unit Tests")
class EventDeserializationTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
            // JacksonConfig와 동일한 설정 - 알 수 없는 필드 무시
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    @DisplayName("LikeEvent deserialization")
    @Nested
    inner class LikeEventDeserialization {

        @DisplayName("deserializes JSON to LikeEvent")
        @Test
        fun `deserializes JSON to LikeEvent`() {
            // given
            val json = """
                {
                    "productId": 100,
                    "userId": 1
                }
            """.trimIndent()

            // when
            val event: LikeEvent = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(100L)
            assertThat(event.userId).isEqualTo(1L)
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
            val event: LikeEvent = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(200L)
            assertThat(event.userId).isEqualTo(2L)
        }
    }

    @DisplayName("OrderPaidEvent deserialization")
    @Nested
    inner class OrderPaidEventDeserialization {

        @DisplayName("deserializes JSON to OrderPaidEvent")
        @Test
        fun `deserializes JSON to OrderPaidEvent`() {
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
            val event: OrderPaidEvent = objectMapper.readValue(json)

            // then
            assertThat(event.orderId).isEqualTo(1001L)
            assertThat(event.orderItems).hasSize(2)
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
            val event: OrderPaidEvent = objectMapper.readValue(json)

            // then
            assertThat(event.orderItems[0].productId).isEqualTo(100L)
            assertThat(event.orderItems[0].quantity).isEqualTo(3)
            assertThat(event.orderItems[1].productId).isEqualTo(200L)
            assertThat(event.orderItems[1].quantity).isEqualTo(5)
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
            val event: OrderPaidEvent = objectMapper.readValue(json)

            // then
            assertThat(event.orderItems).isEmpty()
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
            val event: OrderPaidEvent = objectMapper.readValue(json)

            // then
            assertThat(event.orderId).isEqualTo(1004L)
            assertThat(event.orderItems).hasSize(1)
        }
    }

    @DisplayName("ProductViewedEvent deserialization")
    @Nested
    inner class ProductViewedEventDeserialization {

        @DisplayName("deserializes JSON to ProductViewedEvent")
        @Test
        fun `deserializes JSON to ProductViewedEvent`() {
            // given
            val json = """
                {
                    "productId": 500,
                    "userId": 10
                }
            """.trimIndent()

            // when
            val event: ProductViewedEvent = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(500L)
            assertThat(event.userId).isEqualTo(10L)
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
            val event: ProductViewedEvent = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(600L)
            assertThat(event.userId).isEqualTo(20L)
        }
    }

    @DisplayName("StockDepletedEvent deserialization")
    @Nested
    inner class StockDepletedEventDeserialization {

        @DisplayName("deserializes JSON to StockDepletedEvent")
        @Test
        fun `deserializes JSON to StockDepletedEvent`() {
            // given
            val json = """
                {
                    "productId": 999
                }
            """.trimIndent()

            // when
            val event: StockDepletedEvent = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(999L)
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
            val event: StockDepletedEvent = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(888L)
        }
    }
}
