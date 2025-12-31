package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.eventschema.CloudEventEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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
    @DisplayName("toCommandItems - VIEW 이벤트 변환")
    inner class ViewEventConversionTest {
        @Test
        @DisplayName("product.viewed 이벤트를 viewDelta=1인 Item으로 변환한다")
        fun `maps product viewed event to Item with viewDelta=1`() {
            // given
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100, "userId": 1}""",
                time = eventTime,
            )

            // when
            val items = rankingEventMapper.toCommandItems(envelope)

            // then
            assertThat(items).hasSize(1)
            val item = items.first()
            assertThat(item.productId).isEqualTo(100L)
            assertThat(item.viewDelta).isEqualTo(1)
            assertThat(item.likeCreatedDelta).isEqualTo(0)
            assertThat(item.likeCanceledDelta).isEqualTo(0)
            assertThat(item.orderCountDelta).isEqualTo(0)
            assertThat(item.orderAmountDelta).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        @DisplayName("statHour는 Asia/Seoul 타임존으로 변환된다")
        fun `statHour is converted to Asia Seoul timezone`() {
            // given
            val eventTime = Instant.parse("2025-01-01T00:30:00Z") // UTC 00:30 = KST 09:30
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                payload = """{"productId": 100}""",
                time = eventTime,
            )

            // when
            val items = rankingEventMapper.toCommandItems(envelope)

            // then
            val expectedStatHour = ZonedDateTime.ofInstant(eventTime, ZoneId.of("Asia/Seoul"))
            assertThat(items.first().statHour).isEqualTo(expectedStatHour)
        }
    }

    @Nested
    @DisplayName("toCommandItems - LIKE_CREATED 이벤트 변환")
    inner class LikeCreatedEventConversionTest {
        @Test
        @DisplayName("like.created 이벤트를 likeCreatedDelta=1인 Item으로 변환한다")
        fun `maps like created event to Item with likeCreatedDelta=1`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.created.v1",
                payload = """{"productId": 200, "userId": 1}""",
            )

            // when
            val items = rankingEventMapper.toCommandItems(envelope)

            // then
            assertThat(items).hasSize(1)
            val item = items.first()
            assertThat(item.productId).isEqualTo(200L)
            assertThat(item.likeCreatedDelta).isEqualTo(1)
            assertThat(item.viewDelta).isEqualTo(0)
            assertThat(item.likeCanceledDelta).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("toCommandItems - LIKE_CANCELED 이벤트 변환")
    inner class LikeCanceledEventConversionTest {
        @Test
        @DisplayName("like.canceled 이벤트를 likeCanceledDelta=1인 Item으로 변환한다")
        fun `maps like canceled event to Item with likeCanceledDelta=1`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.like.canceled.v1",
                payload = """{"productId": 300, "userId": 1}""",
            )

            // when
            val items = rankingEventMapper.toCommandItems(envelope)

            // then
            assertThat(items).hasSize(1)
            val item = items.first()
            assertThat(item.productId).isEqualTo(300L)
            assertThat(item.likeCanceledDelta).isEqualTo(1)
            assertThat(item.viewDelta).isEqualTo(0)
            assertThat(item.likeCreatedDelta).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("toCommandItems - ORDER_PAID 이벤트 변환")
    inner class OrderPaidEventConversionTest {
        @Test
        @DisplayName("order.paid 이벤트를 상품별 Item으로 변환한다 - unitPrice * quantity로 계산")
        fun `maps order paid event to per-product Items with calculated amount`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                payload = """{"orderId": 1, "userId": 1, "totalAmount": 30000, "orderItems": [{"productId": 100, "quantity": 2, "unitPrice": 10000}, {"productId": 200, "quantity": 1, "unitPrice": 10000}]}""",
            )

            // when
            val items = rankingEventMapper.toCommandItems(envelope)

            // then
            assertThat(items).hasSize(2)

            // 상품 100: unitPrice 10000 * quantity 2 = 20000
            assertThat(items[0].productId).isEqualTo(100L)
            assertThat(items[0].orderCountDelta).isEqualTo(1)
            assertThat(items[0].orderAmountDelta).isEqualByComparingTo(BigDecimal("20000"))

            // 상품 200: unitPrice 10000 * quantity 1 = 10000
            assertThat(items[1].productId).isEqualTo(200L)
            assertThat(items[1].orderCountDelta).isEqualTo(1)
            assertThat(items[1].orderAmountDelta).isEqualByComparingTo(BigDecimal("10000"))
        }

        @Test
        @DisplayName("빈 orderItems 주문 시 빈 Item 리스트를 반환한다")
        fun `returns empty list for order with no items`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                payload = """{"orderId": 3, "userId": 1, "totalAmount": 0, "orderItems": []}""",
            )

            // when
            val items = rankingEventMapper.toCommandItems(envelope)

            // then
            assertThat(items).isEmpty()
        }

        @Test
        @DisplayName("여러 상품의 다양한 quantity와 unitPrice가 각각 정확하게 계산된다")
        fun `multiple items with various quantities and prices are calculated correctly`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                payload = """{"orderId": 5, "userId": 1, "totalAmount": 125000, "orderItems": [{"productId": 100, "quantity": 3, "unitPrice": 25000}, {"productId": 200, "quantity": 2, "unitPrice": 15000}, {"productId": 300, "quantity": 1, "unitPrice": 20000}]}""",
            )

            // when
            val items = rankingEventMapper.toCommandItems(envelope)

            // then
            assertThat(items).hasSize(3)
            // 상품 100: 25000 * 3 = 75000
            assertThat(items[0].productId).isEqualTo(100L)
            assertThat(items[0].orderAmountDelta).isEqualByComparingTo(BigDecimal("75000"))
            // 상품 200: 15000 * 2 = 30000
            assertThat(items[1].productId).isEqualTo(200L)
            assertThat(items[1].orderAmountDelta).isEqualByComparingTo(BigDecimal("30000"))
            // 상품 300: 20000 * 1 = 20000
            assertThat(items[2].productId).isEqualTo(300L)
            assertThat(items[2].orderAmountDelta).isEqualByComparingTo(BigDecimal("20000"))
        }
    }

    @Nested
    @DisplayName("toCommandItems - 지원하지 않는 이벤트 타입")
    inner class UnsupportedEventTypeTest {
        @Test
        @DisplayName("지원하지 않는 이벤트 타입은 빈 리스트를 반환한다")
        fun `returns empty list for unsupported event type`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.unknown.event.v1",
                payload = """{"productId": 100}""",
            )

            // when
            val items = rankingEventMapper.toCommandItems(envelope)

            // then
            assertThat(items).isEmpty()
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
}
