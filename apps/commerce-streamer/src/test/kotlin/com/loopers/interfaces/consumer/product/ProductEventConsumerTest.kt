package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.domain.product.ProductStatisticService
import com.loopers.domain.product.UpdateLikeCountCommand
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.product.ProductCacheRepository
import com.loopers.interfaces.consumer.product.event.LikeEventPayload
import com.loopers.interfaces.consumer.product.event.OrderPaidEventPayload
import com.loopers.interfaces.consumer.product.event.ProductViewedEventPayload
import com.loopers.interfaces.consumer.product.event.StockDepletedEventPayload
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

@DisplayName("ProductEventConsumer 배치 처리 단위 테스트")
class ProductEventConsumerTest {

    private lateinit var productStatisticService: ProductStatisticService
    private lateinit var productCacheRepository: ProductCacheRepository
    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var productEventMapper: ProductEventMapper
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
        productEventMapper = ProductEventMapper(objectMapper)
        acknowledgment = mockk()

        productEventConsumer = ProductEventConsumer(
            productStatisticService,
            productCacheRepository,
            eventHandledRepository,
            productEventMapper,
            objectMapper,
        )
    }

    @DisplayName("혼합 이벤트 배치 처리")
    @Nested
    inner class MixedEventBatch {

        @DisplayName("여러 타입의 이벤트가 섞인 배치를 타입별로 그룹화하여 처리한다")
        @Test
        fun `groups mixed event batch by type and processes each category`() {
            // given
            val likePayload = LikeEventPayload(productId = 1L, userId = 1L)
            val likeEnvelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )

            val orderPayload = OrderPaidEventPayload(
                orderId = 100L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 2L, quantity = 3)),
            )
            val orderEnvelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(orderPayload),
            )

            val viewPayload = ProductViewedEventPayload(productId = 3L, userId = 1L)
            val viewEnvelope = createEnvelope(
                id = "view-event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "3",
                payload = objectMapper.writeValueAsString(viewPayload),
            )

            val stockPayload = StockDepletedEventPayload(productId = 4L)
            val stockEnvelope = createEnvelope(
                type = "loopers.stock.depleted.v1",
                aggregateType = "Product",
                aggregateId = "4",
                payload = objectMapper.writeValueAsString(stockPayload),
            )

            val records = listOf(
                createConsumerRecord("like-events", likeEnvelope),
                createConsumerRecord("order-events", orderEnvelope),
                createConsumerRecord("product-view-events", viewEnvelope),
                createConsumerRecord("stock-events", stockEnvelope),
            )

            every { productStatisticService.updateLikeCount(any()) } just runs
            every { eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction("Order", "100", "SALES_COUNT_INCREASED") } returns false
            every { productStatisticService.updateSalesCount(any()) } just runs
            every { eventHandledRepository.save(any()) } returns mockk<EventHandled>()
            every { eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction("ProductView", "3_view-event-1", "VIEW_COUNT_INCREASED") } returns false
            every { productStatisticService.updateViewCount(any()) } just runs
            every { productCacheRepository.evictProductCache(4L) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) { productStatisticService.updateLikeCount(any()) }
            verify(exactly = 1) { productStatisticService.updateSalesCount(any()) }
            verify(exactly = 1) { productStatisticService.updateViewCount(any()) }
            verify(exactly = 1) { productCacheRepository.evictProductCache(4L) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("Like 이벤트 처리")
    @Nested
    inner class HandleLikeEvents {

        @DisplayName("Like 이벤트 배치를 updateLikeCount() 명령으로 변환하여 호출한다")
        @Test
        fun `calls updateLikeCount with command for like events`() {
            // given
            val likeCreatedPayload = LikeEventPayload(productId = 1L, userId = 1L)
            val likeCreatedEnvelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likeCreatedPayload),
            )

            val likeCanceledPayload = LikeEventPayload(productId = 2L, userId = 2L)
            val likeCanceledEnvelope = createEnvelope(
                type = "loopers.like.canceled.v1",
                aggregateType = "Like",
                aggregateId = "2",
                payload = objectMapper.writeValueAsString(likeCanceledPayload),
            )

            val records = listOf(
                createConsumerRecord("like-events", likeCreatedEnvelope),
                createConsumerRecord("like-events", likeCanceledEnvelope),
            )

            every { productStatisticService.updateLikeCount(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateLikeCount(
                    match { command ->
                        command.items.size == 2 &&
                            command.items[0].productId == 1L &&
                            command.items[0].type == UpdateLikeCountCommand.LikeType.CREATED &&
                            command.items[1].productId == 2L &&
                            command.items[1].type == UpdateLikeCountCommand.LikeType.CANCELED
                    },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("OrderPaid 이벤트 처리")
    @Nested
    inner class HandleOrderPaidEvents {

        @DisplayName("미처리 Order Paid 이벤트만 필터링하여 updateSalesCount()를 호출한다")
        @Test
        fun `filters already handled events and calls updateSalesCount for unhandled`() {
            // given
            val orderId1 = 100L
            val orderPayload1 = OrderPaidEventPayload(
                orderId = orderId1,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 1L, quantity = 2)),
            )
            val orderEnvelope1 = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = orderId1.toString(),
                payload = objectMapper.writeValueAsString(orderPayload1),
            )

            val orderId2 = 200L
            val orderPayload2 = OrderPaidEventPayload(
                orderId = orderId2,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 2L, quantity = 3)),
            )
            val orderEnvelope2 = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = orderId2.toString(),
                payload = objectMapper.writeValueAsString(orderPayload2),
            )

            val records = listOf(
                createConsumerRecord("order-events", orderEnvelope1),
                createConsumerRecord("order-events", orderEnvelope2),
            )

            // orderId1 is already handled, orderId2 is not
            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction("Order", "100", "SALES_COUNT_INCREASED")
            } returns true
            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction("Order", "200", "SALES_COUNT_INCREASED")
            } returns false
            every { productStatisticService.updateSalesCount(any()) } just runs
            every { eventHandledRepository.save(any()) } returns mockk<EventHandled>()
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateSalesCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 2L &&
                            command.items[0].quantity == 3
                    },
                )
            }
            verify(exactly = 1) {
                eventHandledRepository.save(
                    match { it.aggregateType == "Order" && it.aggregateId == "200" && it.action == "SALES_COUNT_INCREASED" },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("모든 Order Paid 이벤트가 이미 처리된 경우 updateSalesCount()를 호출하지 않는다")
        @Test
        fun `does not call updateSalesCount when all events already handled`() {
            // given
            val orderPayload = OrderPaidEventPayload(
                orderId = 100L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 1L, quantity = 2)),
            )
            val orderEnvelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(orderPayload),
            )

            val records = listOf(createConsumerRecord("order-events", orderEnvelope))

            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction("Order", "100", "SALES_COUNT_INCREASED")
            } returns true
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateSalesCount(any()) }
            verify(exactly = 0) { eventHandledRepository.save(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("ProductViewed 이벤트 처리")
    @Nested
    inner class HandleProductViewedEvents {

        @DisplayName("미처리 Product Viewed 이벤트만 필터링하여 updateViewCount()를 호출한다")
        @Test
        fun `filters already handled events and calls updateViewCount for unhandled`() {
            // given
            val productId1 = 1L
            val eventId1 = "event-1"
            val viewPayload1 = ProductViewedEventPayload(productId = productId1, userId = 1L)
            val viewEnvelope1 = createEnvelope(
                id = eventId1,
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = productId1.toString(),
                payload = objectMapper.writeValueAsString(viewPayload1),
            )

            val productId2 = 2L
            val eventId2 = "event-2"
            val viewPayload2 = ProductViewedEventPayload(productId = productId2, userId = 2L)
            val viewEnvelope2 = createEnvelope(
                id = eventId2,
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = productId2.toString(),
                payload = objectMapper.writeValueAsString(viewPayload2),
            )

            val records = listOf(
                createConsumerRecord("product-view-events", viewEnvelope1),
                createConsumerRecord("product-view-events", viewEnvelope2),
            )

            // event-1 is already handled, event-2 is not
            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction("ProductView", "1_event-1", "VIEW_COUNT_INCREASED")
            } returns true
            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction("ProductView", "2_event-2", "VIEW_COUNT_INCREASED")
            } returns false
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledRepository.save(any()) } returns mockk<EventHandled>()
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateViewCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == productId2
                    },
                )
            }
            verify(exactly = 1) {
                eventHandledRepository.save(
                    match { it.aggregateType == "ProductView" && it.aggregateId == "2_event-2" && it.action == "VIEW_COUNT_INCREASED" },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("모든 Product Viewed 이벤트가 이미 처리된 경우 updateViewCount()를 호출하지 않는다")
        @Test
        fun `does not call updateViewCount when all events already handled`() {
            // given
            val viewPayload = ProductViewedEventPayload(productId = 1L, userId = 1L)
            val viewEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(viewPayload),
            )

            val records = listOf(createConsumerRecord("product-view-events", viewEnvelope))

            every {
                eventHandledRepository.existsByAggregateTypeAndAggregateIdAndAction("ProductView", "1_event-1", "VIEW_COUNT_INCREASED")
            } returns true
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateViewCount(any()) }
            verify(exactly = 0) { eventHandledRepository.save(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("StockDepleted 이벤트 처리")
    @Nested
    inner class HandleStockDepletedEvents {

        @DisplayName("Stock Depleted 이벤트의 각 productId에 대해 캐시를 무효화한다")
        @Test
        fun `evicts cache for each productId in stock depleted events`() {
            // given
            val stockPayload1 = StockDepletedEventPayload(productId = 1L)
            val stockEnvelope1 = createEnvelope(
                type = "loopers.stock.depleted.v1",
                aggregateType = "Product",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(stockPayload1),
            )

            val stockPayload2 = StockDepletedEventPayload(productId = 2L)
            val stockEnvelope2 = createEnvelope(
                type = "loopers.stock.depleted.v1",
                aggregateType = "Product",
                aggregateId = "2",
                payload = objectMapper.writeValueAsString(stockPayload2),
            )

            val records = listOf(
                createConsumerRecord("stock-events", stockEnvelope1),
                createConsumerRecord("stock-events", stockEnvelope2),
            )

            every { productCacheRepository.evictProductCache(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) { productCacheRepository.evictProductCache(1L) }
            verify(exactly = 1) { productCacheRepository.evictProductCache(2L) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("잘못된 메시지 처리")
    @Nested
    inner class HandleMalformedMessage {

        @DisplayName("파싱 실패한 메시지는 로그 후 건너뛰고 나머지 메시지를 처리한다")
        @Test
        fun `logs malformed message and processes remaining messages`() {
            // given
            val malformedRecord = ConsumerRecord(
                "like-events",
                0,
                0L,
                "key",
                "not a valid json",
            )

            val validPayload = LikeEventPayload(productId = 1L, userId = 1L)
            val validEnvelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(validPayload),
            )
            val validRecord = createConsumerRecord("like-events", validEnvelope)

            val records = listOf(malformedRecord, validRecord)

            every { productStatisticService.updateLikeCount(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) { productStatisticService.updateLikeCount(any()) }
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
