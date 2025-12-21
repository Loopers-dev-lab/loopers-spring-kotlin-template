package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.domain.product.ProductStatisticService
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.interfaces.consumer.product.event.ProductViewedEventPayload
import com.loopers.support.idempotency.EventHandledService
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

@DisplayName("ProductViewEventConsumer batch processing unit test")
class ProductViewEventConsumerTest {

    private lateinit var productStatisticService: ProductStatisticService
    private lateinit var productEventMapper: ProductEventMapper
    private lateinit var eventHandledService: EventHandledService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var productViewEventConsumer: ProductViewEventConsumer

    @BeforeEach
    fun setUp() {
        productStatisticService = mockk()
        eventHandledService = mockk()
        objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        productEventMapper = ProductEventMapper(objectMapper)
        acknowledgment = mockk()

        productViewEventConsumer = ProductViewEventConsumer(
            productStatisticService,
            productEventMapper,
            eventHandledService,
            objectMapper,
        )
    }

    @DisplayName("Product Viewed event processing")
    @Nested
    inner class HandleProductViewedEvents {

        @DisplayName("Calls updateViewCount with command for product viewed events")
        @Test
        fun `calls updateViewCount with command for product viewed events`() {
            // given
            val viewedPayload = ProductViewedEventPayload(
                productId = 100L,
                userId = 1L,
            )
            val viewedEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(viewedPayload),
            )

            val records = listOf(createConsumerRecord("product-events", viewedEnvelope))

            every { eventHandledService.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledService.markAllAsHandled(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateViewCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 100L
                    },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("Processes multiple view events in a batch")
        @Test
        fun `processes multiple view events in a batch`() {
            // given
            val viewedPayload1 = ProductViewedEventPayload(productId = 100L, userId = 1L)
            val viewedEnvelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(viewedPayload1),
            )

            val viewedPayload2 = ProductViewedEventPayload(productId = 200L, userId = 2L)
            val viewedEnvelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "200",
                payload = objectMapper.writeValueAsString(viewedPayload2),
            )

            val records = listOf(
                createConsumerRecord("product-events", viewedEnvelope1),
                createConsumerRecord("product-events", viewedEnvelope2),
            )

            every { eventHandledService.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledService.markAllAsHandled(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateViewCount(
                    match { command ->
                        command.items.size == 2 &&
                            command.items.any { it.productId == 100L } &&
                            command.items.any { it.productId == 200L }
                    },
                )
            }
        }

        @DisplayName("Does not call updateViewCount for empty batch")
        @Test
        fun `does not call updateViewCount for empty batch`() {
            // given
            val records = emptyList<ConsumerRecord<String, String>>()

            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateViewCount(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("Filter unsupported events")
    @Nested
    inner class FilterUnsupportedEvents {

        @DisplayName("Silently skips unsupported event types")
        @Test
        fun `silently skips unsupported event types`() {
            // given
            val unsupportedEnvelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "100",
                payload = "{}",
            )

            val records = listOf(createConsumerRecord("product-events", unsupportedEnvelope))

            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateViewCount(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("Processes only supported events when mixed with unsupported")
        @Test
        fun `processes only supported events when mixed with unsupported`() {
            // given
            val viewedPayload = ProductViewedEventPayload(productId = 100L, userId = 1L)
            val viewedEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(viewedPayload),
            )

            val unsupportedEnvelope = createEnvelope(
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "100",
                payload = "{}",
            )

            val records = listOf(
                createConsumerRecord("product-events", viewedEnvelope),
                createConsumerRecord("product-events", unsupportedEnvelope),
            )

            every { eventHandledService.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledService.markAllAsHandled(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateViewCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 100L
                    },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("DB idempotency check")
    @Nested
    inner class DatabaseIdempotencyCheck {

        @DisplayName("Skips already processed events")
        @Test
        fun `skips already processed events`() {
            // given
            val viewedPayload = ProductViewedEventPayload(productId = 100L, userId = 1L)
            val viewedEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(viewedPayload),
            )

            val records = listOf(createConsumerRecord("product-events", viewedEnvelope))

            every { eventHandledService.findAllExistingKeys(any()) } answers { firstArg() }
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateViewCount(any()) }
            verify(exactly = 0) { eventHandledService.markAllAsHandled(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("Processes only unprocessed events when some are already handled")
        @Test
        fun `processes only unprocessed events when some are already handled`() {
            // given
            val processedPayload = ProductViewedEventPayload(productId = 100L, userId = 1L)
            val processedEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(processedPayload),
            )

            val newPayload = ProductViewedEventPayload(productId = 200L, userId = 2L)
            val newEnvelope = createEnvelope(
                id = "event-2",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "200",
                payload = objectMapper.writeValueAsString(newPayload),
            )

            val records = listOf(
                createConsumerRecord("product-events", processedEnvelope),
                createConsumerRecord("product-events", newEnvelope),
            )

            every { eventHandledService.findAllExistingKeys(any()) } returns setOf("product-statistic:event-1")
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledService.markAllAsHandled(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateViewCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 200L
                    },
                )
            }
            verify(exactly = 1) {
                eventHandledService.markAllAsHandled(
                    match { list ->
                        list.size == 1 &&
                            list[0] == "product-statistic:event-2"
                    },
                )
            }
        }

        @DisplayName("Saves idempotency keys after processing")
        @Test
        fun `saves idempotency keys after processing`() {
            // given
            val viewedPayload = ProductViewedEventPayload(productId = 100L, userId = 1L)
            val viewedEnvelope = createEnvelope(
                id = "event-abc-123",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(viewedPayload),
            )

            val records = listOf(createConsumerRecord("product-events", viewedEnvelope))

            every { eventHandledService.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledService.markAllAsHandled(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                eventHandledService.markAllAsHandled(
                    match { list ->
                        list.size == 1 &&
                            list[0] == "product-statistic:event-abc-123"
                    },
                )
            }
        }
    }

    @DisplayName("Idempotency key format")
    @Nested
    inner class IdempotencyKeyFormat {

        @DisplayName("Idempotency key follows CONSUMER_GROUP:eventId format")
        @Test
        fun `idempotency key follows correct format`() {
            // given
            val viewedPayload = ProductViewedEventPayload(productId = 100L, userId = 1L)
            val viewedEnvelope = createEnvelope(
                id = "unique-event-uuid",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(viewedPayload),
            )

            val records = listOf(createConsumerRecord("product-events", viewedEnvelope))

            every { eventHandledService.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledService.markAllAsHandled(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                eventHandledService.markAllAsHandled(
                    match { list ->
                        list.size == 1 &&
                            list[0] == "product-statistic:unique-event-uuid"
                    },
                )
            }
        }
    }

    @DisplayName("Parsing failure handling")
    @Nested
    inner class HandleParsingFailure {

        @DisplayName("Throws BatchListenerFailedException for malformed message")
        @Test
        fun `throws BatchListenerFailedException for malformed message`() {
            // given
            val malformedRecord = ConsumerRecord(
                "product-events",
                0,
                0L,
                "key",
                "not a valid json",
            )

            val records = listOf(malformedRecord)

            // when & then
            assertThrows<BatchListenerFailedException> {
                productViewEventConsumer.consume(records, acknowledgment)
            }
        }

        @DisplayName("Does not process remaining messages when first message fails")
        @Test
        fun `does not process remaining messages when first message fails`() {
            // given
            val malformedRecord = ConsumerRecord(
                "product-events",
                0,
                0L,
                "key",
                "not a valid json",
            )

            val viewedPayload = ProductViewedEventPayload(productId = 100L, userId = 1L)
            val validEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(viewedPayload),
            )
            val validRecord = createConsumerRecord("product-events", validEnvelope)

            val records = listOf(malformedRecord, validRecord)

            // when & then
            assertThrows<BatchListenerFailedException> {
                productViewEventConsumer.consume(records, acknowledgment)
            }
            verify(exactly = 0) { productStatisticService.updateViewCount(any()) }
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
