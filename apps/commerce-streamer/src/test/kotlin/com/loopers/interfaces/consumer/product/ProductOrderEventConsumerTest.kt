package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.domain.product.ProductStatisticService
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.interfaces.consumer.product.event.OrderPaidEventPayload
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
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import java.time.Instant

@DisplayName("ProductOrderEventConsumer 배치 처리 단위 테스트")
class ProductOrderEventConsumerTest {

    private lateinit var productStatisticService: ProductStatisticService
    private lateinit var productEventMapper: ProductEventMapper
    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var productOrderEventConsumer: ProductOrderEventConsumer

    @BeforeEach
    fun setUp() {
        productStatisticService = mockk()
        eventHandledRepository = mockk()
        objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        productEventMapper = ProductEventMapper(objectMapper)
        acknowledgment = mockk()

        productOrderEventConsumer = ProductOrderEventConsumer(
            productStatisticService,
            productEventMapper,
            eventHandledRepository,
            objectMapper,
        )
    }

    @DisplayName("Order Paid 이벤트 처리")
    @Nested
    inner class HandleOrderPaidEvents {

        @DisplayName("Order Paid 이벤트 배치를 updateSalesCount() 명령으로 변환하여 호출한다")
        @Test
        fun `calls updateSalesCount with command for order paid events`() {
            // given
            val orderPaidPayload = OrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(
                    OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 2),
                    OrderPaidEventPayload.OrderItem(productId = 200L, quantity = 1),
                ),
            )
            val orderPaidEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(orderPaidPayload),
            )

            val records = listOf(createConsumerRecord("order-events", orderPaidEnvelope))

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateSalesCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateSalesCount(
                    match { command ->
                        command.items.size == 2 &&
                            command.items[0].productId == 100L &&
                            command.items[0].quantity == 2 &&
                            command.items[1].productId == 200L &&
                            command.items[1].quantity == 1
                    },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("빈 배치인 경우 updateSalesCount()를 호출하지 않는다")
        @Test
        fun `does not call updateSalesCount for empty batch`() {
            // given
            val records = emptyList<ConsumerRecord<String, String>>()

            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateSalesCount(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("지원하지 않는 이벤트 필터링")
    @Nested
    inner class FilterUnsupportedEvents {

        @DisplayName("지원하지 않는 이벤트 타입은 조용히 건너뛴다")
        @Test
        fun `silently skips unsupported event types`() {
            // given
            val unsupportedEnvelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "100",
                payload = "{}",
            )

            val records = listOf(createConsumerRecord("order-events", unsupportedEnvelope))

            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateSalesCount(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("지원하는 이벤트와 지원하지 않는 이벤트가 섞인 경우 지원하는 이벤트만 처리한다")
        @Test
        fun `processes only supported events when mixed with unsupported`() {
            // given
            val orderPayload = OrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 1)),
            )
            val orderEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(orderPayload),
            )

            val unsupportedEnvelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "100",
                payload = "{}",
            )

            val records = listOf(
                createConsumerRecord("order-events", orderEnvelope),
                createConsumerRecord("order-events", unsupportedEnvelope),
            )

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateSalesCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateSalesCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 100L
                    },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("배치 내 중복 제거")
    @Nested
    inner class BatchDeduplication {

        @DisplayName("같은 aggregateId의 이벤트가 배치 내에서 중복될 경우 최신 시간의 이벤트만 처리한다")
        @Test
        fun `keeps only latest event by time for same aggregate`() {
            // given
            val olderPayload = OrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 1)),
            )
            val olderEnvelope = createEnvelope(
                id = "event-old",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(olderPayload),
                time = Instant.parse("2024-01-01T10:00:00Z"),
            )

            val newerPayload = OrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 200L, quantity = 5)),
            )
            val newerEnvelope = createEnvelope(
                id = "event-new",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(newerPayload),
                time = Instant.parse("2024-01-01T12:00:00Z"),
            )

            val records = listOf(
                createConsumerRecord("order-events", olderEnvelope),
                createConsumerRecord("order-events", newerEnvelope),
            )

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateSalesCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateSalesCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 200L &&
                            command.items[0].quantity == 5
                    },
                )
            }
        }

        @DisplayName("서로 다른 aggregateId의 이벤트는 모두 처리한다")
        @Test
        fun `processes all events with different aggregate ids`() {
            // given
            val payload1 = OrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 1)),
            )
            val envelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(payload1),
            )

            val payload2 = OrderPaidEventPayload(
                orderId = 2L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 200L, quantity = 2)),
            )
            val envelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "2",
                payload = objectMapper.writeValueAsString(payload2),
            )

            val records = listOf(
                createConsumerRecord("order-events", envelope1),
                createConsumerRecord("order-events", envelope2),
            )

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateSalesCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateSalesCount(
                    match { command ->
                        command.items.size == 2 &&
                            command.items.any { it.productId == 100L } &&
                            command.items.any { it.productId == 200L }
                    },
                )
            }
        }
    }

    @DisplayName("DB 멱등성 체크")
    @Nested
    inner class DatabaseIdempotencyCheck {

        @DisplayName("이미 처리된 이벤트는 건너뛴다")
        @Test
        fun `skips already processed events`() {
            // given
            val orderPayload = OrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 1)),
            )
            val orderEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(orderPayload),
            )

            val records = listOf(createConsumerRecord("order-events", orderEnvelope))

            every { eventHandledRepository.findAllExistingKeys(any()) } answers { firstArg() }
            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateSalesCount(any()) }
            verify(exactly = 0) { eventHandledRepository.saveAll(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("일부만 처리된 이벤트가 있으면 미처리된 이벤트만 처리한다")
        @Test
        fun `processes only unprocessed events when some are already handled`() {
            // given
            val processedPayload = OrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 1)),
            )
            val processedEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(processedPayload),
            )

            val newPayload = OrderPaidEventPayload(
                orderId = 2L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 200L, quantity = 2)),
            )
            val newEnvelope = createEnvelope(
                id = "event-2",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "2",
                payload = objectMapper.writeValueAsString(newPayload),
            )

            val records = listOf(
                createConsumerRecord("order-events", processedEnvelope),
                createConsumerRecord("order-events", newEnvelope),
            )

            every { eventHandledRepository.findAllExistingKeys(any()) } returns setOf("product-statistic:Order:1:paid")
            every { productStatisticService.updateSalesCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateSalesCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 200L
                    },
                )
            }
            verify(exactly = 1) { eventHandledRepository.saveAll(any()) }
        }

        @DisplayName("처리 후 멱등성 키를 저장한다")
        @Test
        fun `saves idempotency keys after processing`() {
            // given
            val orderPayload = OrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 1)),
            )
            val orderEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(orderPayload),
            )

            val records = listOf(createConsumerRecord("order-events", orderEnvelope))

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateSalesCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                eventHandledRepository.saveAll(
                    match { list ->
                        list.size == 1 && list[0].idempotencyKey == "product-statistic:Order:1:paid"
                    },
                )
            }
        }
    }

    @DisplayName("멱등성 키 형식")
    @Nested
    inner class IdempotencyKeyFormat {

        @DisplayName("멱등성 키는 CONSUMER_GROUP:aggregateType:aggregateId:eventType 형식이다")
        @Test
        fun `idempotency key follows correct format`() {
            // given
            val orderPayload = OrderPaidEventPayload(
                orderId = 123L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 1)),
            )
            val orderEnvelope = createEnvelope(
                id = "event-id-abc",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "123",
                payload = objectMapper.writeValueAsString(orderPayload),
            )

            val records = listOf(createConsumerRecord("order-events", orderEnvelope))

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateSalesCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
            every { acknowledgment.acknowledge() } just runs

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                eventHandledRepository.saveAll(
                    match { list ->
                        list.size == 1 && list[0].idempotencyKey == "product-statistic:Order:123:paid"
                    },
                )
            }
        }
    }

    @DisplayName("파싱 실패 처리")
    @Nested
    inner class HandleParsingFailure {

        @DisplayName("파싱 실패한 메시지는 BatchListenerFailedException을 발생시킨다")
        @Test
        fun `throws BatchListenerFailedException for malformed message`() {
            // given
            val malformedRecord = ConsumerRecord(
                "order-events",
                0,
                0L,
                "key",
                "not a valid json",
            )

            val records = listOf(malformedRecord)

            // when & then
            assertThrows<BatchListenerFailedException> {
                productOrderEventConsumer.consume(records, acknowledgment)
            }
        }

        @DisplayName("첫 번째 메시지가 파싱 실패하면 나머지 메시지는 처리되지 않는다")
        @Test
        fun `does not process remaining messages when first message fails`() {
            // given
            val malformedRecord = ConsumerRecord(
                "order-events",
                0,
                0L,
                "key",
                "not a valid json",
            )

            val orderPayload = OrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 1)),
            )
            val validEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(orderPayload),
            )
            val validRecord = createConsumerRecord("order-events", validEnvelope)

            val records = listOf(malformedRecord, validRecord)

            // when & then
            assertThrows<BatchListenerFailedException> {
                productOrderEventConsumer.consume(records, acknowledgment)
            }
            verify(exactly = 0) { productStatisticService.updateSalesCount(any()) }
        }
    }

    private fun createEnvelope(
        id: String = "test-event-id",
        type: String,
        aggregateType: String,
        aggregateId: String,
        payload: String,
        time: Instant = Instant.now(),
    ) = CloudEventEnvelope(
        id = id,
        type = type,
        source = "test-source",
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        time = time,
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
