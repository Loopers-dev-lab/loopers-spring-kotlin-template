package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.domain.product.OrderItemSnapshot
import com.loopers.domain.product.ProductStatisticService
import com.loopers.infrastructure.product.ProductCacheRepository
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.interfaces.consumer.dto.LikeCanceledEventPayload
import com.loopers.interfaces.consumer.dto.LikeCreatedEventPayload
import com.loopers.interfaces.consumer.dto.OrderPaidEventPayload
import com.loopers.interfaces.consumer.dto.ProductViewedEventPayload
import com.loopers.interfaces.consumer.dto.StockDepletedEventPayload
import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import java.time.Instant

@DisplayName("ProductEventConsumer 단위 테스트")
class ProductEventConsumerTest {

    private lateinit var productStatisticService: ProductStatisticService
    private lateinit var productCacheRepository: ProductCacheRepository
    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var productEventConsumer: ProductEventConsumer

    @BeforeEach
    fun setUp() {
        productStatisticService = mockk()
        productCacheRepository = mockk()
        eventHandledRepository = mockk()
        objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        acknowledgment = mockk()

        productEventConsumer = ProductEventConsumer(
            productStatisticService,
            productCacheRepository,
            eventHandledRepository,
            objectMapper,
        )
    }

    @DisplayName("LikeCreatedEvent 처리")
    @Nested
    inner class HandleLikeCreated {

        @DisplayName("좋아요 생성 이벤트를 수신하면 increaseLikeCount()를 호출한다")
        @Test
        fun `calls increaseLikeCount when receiving LikeCreatedEvent`() {
            // given
            val productId = 100L
            val payload = LikeCreatedEventPayload(
                userId = 1L,
                productId = productId,
                occurredAt = Instant.now(),
            )
            val envelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(payload),
            )
            val record = createConsumerRecord("like-events", envelope)

            every { productStatisticService.increaseLikeCount(productId) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { productStatisticService.increaseLikeCount(productId) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("LikeCanceledEvent 처리")
    @Nested
    inner class HandleLikeCanceled {

        @DisplayName("좋아요 취소 이벤트를 수신하면 decreaseLikeCount()를 호출한다")
        @Test
        fun `calls decreaseLikeCount when receiving LikeCanceledEvent`() {
            // given
            val productId = 100L
            val payload = LikeCanceledEventPayload(
                userId = 1L,
                productId = productId,
                occurredAt = Instant.now(),
            )
            val envelope = createEnvelope(
                type = "loopers.like.canceled.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(payload),
            )
            val record = createConsumerRecord("like-events", envelope)

            every { productStatisticService.decreaseLikeCount(productId) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { productStatisticService.decreaseLikeCount(productId) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("OrderPaidEvent 처리")
    @Nested
    inner class HandleOrderPaid {

        @DisplayName("미처리 이벤트인 경우 increaseSalesCount()를 호출하고 markAsHandled한다")
        @Test
        fun `calls increaseSalesCount and marks as handled when event not yet handled`() {
            // given
            val orderId = 123L
            val orderItems = listOf(
                OrderPaidEventPayload.OrderItemPayload(productId = 1L, quantity = 2),
                OrderPaidEventPayload.OrderItemPayload(productId = 2L, quantity = 3),
            )
            val payload = OrderPaidEventPayload(
                orderId = orderId,
                userId = 1L,
                totalAmount = 10000L,
                orderItems = orderItems,
                occurredAt = Instant.now(),
            )
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = orderId.toString(),
                payload = objectMapper.writeValueAsString(payload),
            )
            val record = createConsumerRecord("order-events", envelope)

            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction(
                    "Order",
                    orderId.toString(),
                    "SALES_COUNT_INCREASED",
                )
            } returns false
            every { productStatisticService.increaseSalesCount(any()) } just runs
            every { eventHandledRepository.save(any()) } returns mockk<EventHandled>()
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.increaseSalesCount(
                    match { items ->
                        items.size == 2 &&
                            items[0] == OrderItemSnapshot(1L, 2) &&
                            items[1] == OrderItemSnapshot(2L, 3)
                    },
                )
            }
            verify(exactly = 1) {
                eventHandledRepository.save(
                    match { it.aggregateType == "Order" && it.aggregateId == orderId.toString() && it.action == "SALES_COUNT_INCREASED" },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("이미 처리된 이벤트인 경우 increaseSalesCount()를 호출하지 않는다")
        @Test
        fun `does not call increaseSalesCount when event already handled`() {
            // given
            val orderId = 123L
            val orderItems = listOf(
                OrderPaidEventPayload.OrderItemPayload(productId = 1L, quantity = 2),
            )
            val payload = OrderPaidEventPayload(
                orderId = orderId,
                userId = 1L,
                totalAmount = 10000L,
                orderItems = orderItems,
                occurredAt = Instant.now(),
            )
            val envelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = orderId.toString(),
                payload = objectMapper.writeValueAsString(payload),
            )
            val record = createConsumerRecord("order-events", envelope)

            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction(
                    "Order",
                    orderId.toString(),
                    "SALES_COUNT_INCREASED",
                )
            } returns true
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.increaseSalesCount(any()) }
            verify(exactly = 0) { eventHandledRepository.save(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("ProductViewedEvent 처리")
    @Nested
    inner class HandleProductViewed {

        @DisplayName("미처리 이벤트인 경우 increaseViewCount()를 호출하고 markAsHandled한다")
        @Test
        fun `calls increaseViewCount and marks as handled when event not yet handled`() {
            // given
            val productId = 100L
            val eventId = "event-uuid-123"
            val payload = ProductViewedEventPayload(
                productId = productId,
                userId = 1L,
                occurredAt = Instant.now(),
            )
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = productId.toString(),
                payload = objectMapper.writeValueAsString(payload),
            )
            val record = createConsumerRecord("product-view-events", envelope)
            val expectedAggregateId = "${productId}_$eventId"

            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction(
                    "ProductView",
                    expectedAggregateId,
                    "VIEW_COUNT_INCREASED",
                )
            } returns false
            every { productStatisticService.increaseViewCount(productId) } just runs
            every { eventHandledRepository.save(any()) } returns mockk<EventHandled>()
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { productStatisticService.increaseViewCount(productId) }
            verify(exactly = 1) {
                eventHandledRepository.save(
                    match {
                        it.aggregateType == "ProductView" &&
                            it.aggregateId == expectedAggregateId &&
                            it.action == "VIEW_COUNT_INCREASED"
                    },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("이미 처리된 이벤트인 경우 increaseViewCount()를 호출하지 않는다")
        @Test
        fun `does not call increaseViewCount when event already handled`() {
            // given
            val productId = 100L
            val eventId = "event-uuid-123"
            val payload = ProductViewedEventPayload(
                productId = productId,
                userId = 1L,
                occurredAt = Instant.now(),
            )
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = productId.toString(),
                payload = objectMapper.writeValueAsString(payload),
            )
            val record = createConsumerRecord("product-view-events", envelope)
            val expectedAggregateId = "${productId}_$eventId"

            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction(
                    "ProductView",
                    expectedAggregateId,
                    "VIEW_COUNT_INCREASED",
                )
            } returns true
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.increaseViewCount(any()) }
            verify(exactly = 0) { eventHandledRepository.save(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("StockDepletedEvent 처리")
    @Nested
    inner class HandleStockDepleted {

        @DisplayName("재고 소진 이벤트를 수신하면 evictProductCache()를 호출한다")
        @Test
        fun `calls evictProductCache when receiving StockDepletedEvent`() {
            // given
            val productId = 100L
            val payload = StockDepletedEventPayload(
                productId = productId,
                occurredAt = Instant.now(),
            )
            val envelope = createEnvelope(
                type = "loopers.stock.depleted.v1",
                aggregateType = "Product",
                aggregateId = productId.toString(),
                payload = objectMapper.writeValueAsString(payload),
            )
            val record = createConsumerRecord("stock-events", envelope)

            every { productCacheRepository.evictProductCache(productId) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 1) { productCacheRepository.evictProductCache(productId) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("알 수 없는 이벤트 타입 처리")
    @Nested
    inner class HandleUnknownEvent {

        @DisplayName("알 수 없는 이벤트 타입은 무시하고 acknowledge 한다")
        @Test
        fun `ignores unknown event type and acknowledges`() {
            // given
            val envelope = createEnvelope(
                type = "unknown.event.type",
                aggregateType = "Unknown",
                aggregateId = "1",
                payload = "{}",
            )
            val record = createConsumerRecord("some-topic", envelope)

            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(listOf(record), acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.increaseLikeCount(any()) }
            verify(exactly = 0) { productStatisticService.decreaseLikeCount(any()) }
            verify(exactly = 0) { productStatisticService.increaseSalesCount(any()) }
            verify(exactly = 0) { productStatisticService.increaseViewCount(any()) }
            verify(exactly = 0) { productCacheRepository.evictProductCache(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    private fun createEnvelope(
        id: String = "test-event-id",
        type: String,
        aggregateType: String,
        aggregateId: String,
        payload: String,
    ) = CloudEventEnvelope(
        id = id,
        type = type,
        source = "test-source",
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        time = Instant.now(),
        payload = payload,
    )

    private fun createConsumerRecord(
        topic: String,
        envelope: CloudEventEnvelope,
    ): ConsumerRecord<String, String> = ConsumerRecord(
        topic,
        0,
        0L,
        "key",
        objectMapper.writeValueAsString(envelope),
    )
}
