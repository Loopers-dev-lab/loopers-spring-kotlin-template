package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.domain.ranking.MetricType
import com.loopers.eventschema.CloudEventEnvelope
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    @DisplayName("toAccumulateMetricItems - VIEW 이벤트 매핑")
    inner class ToAccumulateMetricItemsViewTest {
        @Test
        @DisplayName("product.viewed 이벤트를 VIEW 타입의 Item으로 매핑한다")
        fun `maps product viewed event to VIEW type Item`() {
            // given
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                type = "loopers.product.viewed.v1",
                aggregateId = "100",
                payload = """{"productId": 100, "userId": 1}""",
                time = eventTime,
            )

            // when
            val items = rankingEventMapper.toAccumulateMetricItems(envelope)

            // then
            assertThat(items).hasSize(1)
            assertThat(items[0].productId).isEqualTo(100L)
            assertThat(items[0].metricType).isEqualTo(MetricType.VIEW)
            assertThat(items[0].orderAmount).isNull()
            assertThat(items[0].occurredAt).isEqualTo(eventTime)
        }
    }

    @Nested
    @DisplayName("toAccumulateMetricItems - LIKE_CREATED 이벤트 매핑")
    inner class ToAccumulateMetricItemsLikeCreatedTest {
        @Test
        @DisplayName("like.created 이벤트를 LIKE_CREATED 타입의 Item으로 매핑한다")
        fun `maps like created event to LIKE_CREATED type Item`() {
            // given
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateId = "200",
                payload = """{"productId": 200, "userId": 1}""",
                time = eventTime,
            )

            // when
            val items = rankingEventMapper.toAccumulateMetricItems(envelope)

            // then
            assertThat(items).hasSize(1)
            assertThat(items[0].productId).isEqualTo(200L)
            assertThat(items[0].metricType).isEqualTo(MetricType.LIKE_CREATED)
            assertThat(items[0].orderAmount).isNull()
            assertThat(items[0].occurredAt).isEqualTo(eventTime)
        }
    }

    @Nested
    @DisplayName("toAccumulateMetricItems - LIKE_CANCELED 이벤트 매핑")
    inner class ToAccumulateMetricItemsLikeCanceledTest {
        @Test
        @DisplayName("like.canceled 이벤트를 LIKE_CANCELED 타입의 Item으로 매핑한다")
        fun `maps like canceled event to LIKE_CANCELED type Item`() {
            // given
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                type = "loopers.like.canceled.v1",
                aggregateId = "300",
                payload = """{"productId": 300, "userId": 1}""",
                time = eventTime,
            )

            // when
            val items = rankingEventMapper.toAccumulateMetricItems(envelope)

            // then
            assertThat(items).hasSize(1)
            assertThat(items[0].productId).isEqualTo(300L)
            assertThat(items[0].metricType).isEqualTo(MetricType.LIKE_CANCELED)
            assertThat(items[0].orderAmount).isNull()
            assertThat(items[0].occurredAt).isEqualTo(eventTime)
        }
    }

    @Nested
    @DisplayName("toAccumulateMetricItems - ORDER_PAID 이벤트 매핑")
    inner class ToAccumulateMetricItemsOrderPaidTest {
        @Test
        @DisplayName("order.paid 이벤트를 ORDER_PAID 타입의 Item 목록으로 매핑한다")
        fun `maps order paid event to list of ORDER_PAID type Items`() {
            // given
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateId = "order-1",
                payload = """{"orderId": 1, "userId": 1, "totalAmount": 30000, "orderItems": [{"productId": 100, "quantity": 2}, {"productId": 200, "quantity": 1}]}""",
                time = eventTime,
            )

            // when
            val items = rankingEventMapper.toAccumulateMetricItems(envelope)

            // then
            assertThat(items).hasSize(2)

            // totalAmount 30000 / 2 items = 15000 per item
            assertThat(items[0].productId).isEqualTo(100L)
            assertThat(items[0].metricType).isEqualTo(MetricType.ORDER_PAID)
            assertThat(items[0].orderAmount).isEqualByComparingTo(BigDecimal("15000"))
            assertThat(items[0].occurredAt).isEqualTo(eventTime)

            assertThat(items[1].productId).isEqualTo(200L)
            assertThat(items[1].metricType).isEqualTo(MetricType.ORDER_PAID)
            assertThat(items[1].orderAmount).isEqualByComparingTo(BigDecimal("15000"))
            assertThat(items[1].occurredAt).isEqualTo(eventTime)
        }

        @Test
        @DisplayName("단일 상품 주문 시 totalAmount 전체가 해당 상품의 orderAmount가 된다")
        fun `single item order gets full totalAmount as orderAmount`() {
            // given
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateId = "order-2",
                payload = """{"orderId": 2, "userId": 1, "totalAmount": 50000, "orderItems": [{"productId": 100, "quantity": 1}]}""",
                time = eventTime,
            )

            // when
            val items = rankingEventMapper.toAccumulateMetricItems(envelope)

            // then
            assertThat(items).hasSize(1)
            assertThat(items[0].productId).isEqualTo(100L)
            assertThat(items[0].metricType).isEqualTo(MetricType.ORDER_PAID)
            assertThat(items[0].orderAmount).isEqualByComparingTo(BigDecimal("50000"))
        }

        @Test
        @DisplayName("빈 orderItems 주문 시 빈 리스트를 반환한다")
        fun `returns empty list for order with no items`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateId = "order-3",
                payload = """{"orderId": 3, "userId": 1, "totalAmount": 0, "orderItems": []}""",
            )

            // when
            val items = rankingEventMapper.toAccumulateMetricItems(envelope)

            // then
            assertThat(items).isEmpty()
        }

        @Test
        @DisplayName("totalAmount가 orderItems 개수로 나누어 떨어지지 않을 때 정확하게 분배한다")
        fun `distributes totalAmount correctly when not evenly divisible`() {
            // given
            val eventTime = Instant.parse("2025-01-01T10:00:00Z")
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateId = "order-4",
                payload = """{"orderId": 4, "userId": 1, "totalAmount": 10000, "orderItems": [{"productId": 100, "quantity": 1}, {"productId": 200, "quantity": 1}, {"productId": 300, "quantity": 1}]}""",
                time = eventTime,
            )

            // when
            val items = rankingEventMapper.toAccumulateMetricItems(envelope)

            // then
            assertThat(items).hasSize(3)
            // 10000 / 3 = 3333.333...
            val expectedAmount = BigDecimal("10000").divide(BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP)
            items.forEach { item ->
                assertThat(item.orderAmount).isEqualByComparingTo(expectedAmount)
            }
        }
    }

    @Nested
    @DisplayName("toAccumulateMetricItems - 알 수 없는 이벤트 타입")
    inner class ToAccumulateMetricItemsUnknownTypeTest {
        @Test
        @DisplayName("알 수 없는 이벤트 타입에서 예외가 발생한다")
        fun `throws exception for unknown event type`() {
            // given
            val envelope = createEnvelope(
                type = "loopers.unknown.event.v1",
                aggregateId = "100",
                payload = """{"productId": 100}""",
            )

            // when & then
            assertThatThrownBy { rankingEventMapper.toAccumulateMetricItems(envelope) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unknown event type")
                .hasMessageContaining("loopers.unknown.event.v1")
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
