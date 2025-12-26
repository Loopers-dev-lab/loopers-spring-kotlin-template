package com.loopers.domain.ranking.event

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Ranking Event Deserialization Unit Tests")
class RankingEventDeserializationTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().apply {
            registerModule(kotlinModule())
            // JacksonConfig와 동일한 설정 - 알 수 없는 필드 무시
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }

    @DisplayName("RankingProductViewedEventV1 deserialization")
    @Nested
    inner class RankingProductViewedEventV1Deserialization {

        @DisplayName("deserializes JSON to RankingProductViewedEventV1")
        @Test
        fun `deserializes JSON to RankingProductViewedEventV1`() {
            // given
            val json = """
                {
                    "productId": 500
                }
            """.trimIndent()

            // when
            val event: RankingProductViewedEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(500L)
        }

        @DisplayName("ignores extra fields in JSON (userId, occurredAt)")
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
            val event: RankingProductViewedEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(600L)
        }
    }

    @DisplayName("RankingLikeCreatedEventV1 deserialization")
    @Nested
    inner class RankingLikeCreatedEventV1Deserialization {

        @DisplayName("deserializes JSON to RankingLikeCreatedEventV1")
        @Test
        fun `deserializes JSON to RankingLikeCreatedEventV1`() {
            // given
            val json = """
                {
                    "productId": 100
                }
            """.trimIndent()

            // when
            val event: RankingLikeCreatedEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(100L)
        }

        @DisplayName("ignores extra fields in JSON (userId, occurredAt)")
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
            val event: RankingLikeCreatedEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(200L)
        }
    }

    @DisplayName("RankingLikeCanceledEventV1 deserialization")
    @Nested
    inner class RankingLikeCanceledEventV1Deserialization {

        @DisplayName("deserializes JSON to RankingLikeCanceledEventV1")
        @Test
        fun `deserializes JSON to RankingLikeCanceledEventV1`() {
            // given
            val json = """
                {
                    "productId": 300
                }
            """.trimIndent()

            // when
            val event: RankingLikeCanceledEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(300L)
        }

        @DisplayName("ignores extra fields in JSON (userId, occurredAt)")
        @Test
        fun `ignores extra fields in JSON`() {
            // given
            val json = """
                {
                    "productId": 400,
                    "userId": 5,
                    "occurredAt": "2025-01-15T11:00:00Z"
                }
            """.trimIndent()

            // when
            val event: RankingLikeCanceledEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.productId).isEqualTo(400L)
        }
    }

    @DisplayName("RankingOrderPaidEventV1 deserialization")
    @Nested
    inner class RankingOrderPaidEventV1Deserialization {

        @DisplayName("deserializes JSON to RankingOrderPaidEventV1")
        @Test
        fun `deserializes JSON to RankingOrderPaidEventV1`() {
            // given
            val json = """
                {
                    "totalAmount": 50000,
                    "orderItems": [
                        {"productId": 10},
                        {"productId": 20}
                    ]
                }
            """.trimIndent()

            // when
            val event: RankingOrderPaidEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.totalAmount).isEqualTo(50000L)
            assertThat(event.orderItems).hasSize(2)
        }

        @DisplayName("OrderItem is correctly mapped")
        @Test
        fun `orderItems are correctly mapped`() {
            // given
            val json = """
                {
                    "totalAmount": 100000,
                    "orderItems": [
                        {"productId": 100},
                        {"productId": 200}
                    ]
                }
            """.trimIndent()

            // when
            val event: RankingOrderPaidEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.orderItems[0].productId).isEqualTo(100L)
            assertThat(event.orderItems[1].productId).isEqualTo(200L)
        }

        @DisplayName("handles empty orderItems list")
        @Test
        fun `handles empty orderItems list`() {
            // given
            val json = """
                {
                    "totalAmount": 0,
                    "orderItems": []
                }
            """.trimIndent()

            // when
            val event: RankingOrderPaidEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.orderItems).isEmpty()
        }

        @DisplayName("ignores extra fields in JSON (orderId, userId, quantity)")
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
            val event: RankingOrderPaidEventV1 = objectMapper.readValue(json)

            // then
            assertThat(event.totalAmount).isEqualTo(50000L)
            assertThat(event.orderItems).hasSize(1)
            assertThat(event.orderItems[0].productId).isEqualTo(10L)
        }
    }
}
