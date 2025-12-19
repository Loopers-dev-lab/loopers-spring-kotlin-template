package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.domain.product.ProductStatisticService
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.interfaces.consumer.product.event.ProductViewedEventPayload
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

@DisplayName("ProductViewEventConsumer batch processing unit test")
class ProductViewEventConsumerTest {

    private lateinit var productStatisticService: ProductStatisticService
    private lateinit var productEventMapper: ProductEventMapper
    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var productViewEventConsumer: ProductViewEventConsumer

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

        productViewEventConsumer = ProductViewEventConsumer(
            productStatisticService,
            productEventMapper,
            eventHandledRepository,
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

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
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

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
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

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
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

    @DisplayName("Batch deduplication")
    @Nested
    inner class BatchDeduplication {

        @DisplayName("Keeps only latest event by time for same event id")
        @Test
        fun `keeps only latest event by time for same event id`() {
            // given
            val olderPayload = ProductViewedEventPayload(productId = 100L, userId = 1L)
            val olderEnvelope = createEnvelope(
                id = "same-event-id",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(olderPayload),
                time = Instant.parse("2024-01-01T10:00:00Z"),
            )

            val newerPayload = ProductViewedEventPayload(productId = 200L, userId = 2L)
            val newerEnvelope = createEnvelope(
                id = "same-event-id",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "200",
                payload = objectMapper.writeValueAsString(newerPayload),
                time = Instant.parse("2024-01-01T12:00:00Z"),
            )

            val records = listOf(
                createConsumerRecord("product-events", olderEnvelope),
                createConsumerRecord("product-events", newerEnvelope),
            )

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
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
        }

        @DisplayName("Processes all events with different event ids")
        @Test
        fun `processes all events with different event ids`() {
            // given
            val payload1 = ProductViewedEventPayload(productId = 100L, userId = 1L)
            val envelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(payload1),
            )

            val payload2 = ProductViewedEventPayload(productId = 200L, userId = 2L)
            val envelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "200",
                payload = objectMapper.writeValueAsString(payload2),
            )

            val records = listOf(
                createConsumerRecord("product-events", envelope1),
                createConsumerRecord("product-events", envelope2),
            )

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
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

            every { eventHandledRepository.findAllExistingKeys(any()) } answers { firstArg() }
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateViewCount(any()) }
            verify(exactly = 0) { eventHandledRepository.saveAll(any()) }
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

            every { eventHandledRepository.findAllExistingKeys(any()) } returns setOf("product-statistic:event-1")
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
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
                eventHandledRepository.saveAll(
                    match { list ->
                        list.size == 1 &&
                            list[0].idempotencyKey == "product-statistic:event-2"
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

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                eventHandledRepository.saveAll(
                    match { list ->
                        list.size == 1 &&
                            list[0].idempotencyKey == "product-statistic:event-abc-123"
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

            every { eventHandledRepository.findAllExistingKeys(any()) } returns emptySet()
            every { productStatisticService.updateViewCount(any()) } just runs
            every { eventHandledRepository.saveAll(any()) } returns emptyList()
            every { acknowledgment.acknowledge() } just runs

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                eventHandledRepository.saveAll(
                    match { list ->
                        list.size == 1 &&
                            list[0].idempotencyKey == "product-statistic:unique-event-uuid"
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
