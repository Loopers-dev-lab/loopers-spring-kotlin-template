package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.domain.product.ProductStatisticService
import com.loopers.domain.product.UpdateLikeCountCommand
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.interfaces.consumer.product.event.LikeEventPayload
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

@DisplayName("ProductLikeEventConsumer 배치 처리 단위 테스트")
class ProductLikeEventConsumerTest {

    private lateinit var productStatisticService: ProductStatisticService
    private lateinit var productEventMapper: ProductEventMapper
    private lateinit var eventHandledRepository: EventHandledRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var productLikeEventConsumer: ProductLikeEventConsumer

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

        productLikeEventConsumer = ProductLikeEventConsumer(
            productStatisticService,
            productEventMapper,
            eventHandledRepository,
            objectMapper,
        )
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
                id = "event-1",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likeCreatedPayload),
            )

            val likeCanceledPayload = LikeEventPayload(productId = 2L, userId = 2L)
            val likeCanceledEnvelope = createEnvelope(
                id = "event-2",
                type = "loopers.like.canceled.v1",
                aggregateType = "Like",
                aggregateId = "2",
                payload = objectMapper.writeValueAsString(likeCanceledPayload),
            )

            val records = listOf(
                createConsumerRecord("like-events", likeCreatedEnvelope),
                createConsumerRecord("like-events", likeCanceledEnvelope),
            )

            every { eventHandledRepository.existsByIdempotencyKey(any()) } returns false
            every { eventHandledRepository.save(any()) } answers { firstArg() }
            every { productStatisticService.updateLikeCount(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

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

        @DisplayName("빈 배치인 경우 updateLikeCount()를 호출하지 않는다")
        @Test
        fun `does not call updateLikeCount for empty batch`() {
            // given
            val records = emptyList<ConsumerRecord<String, String>>()

            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateLikeCount(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @DisplayName("멱등성 처리")
    @Nested
    inner class HandleIdempotency {

        @DisplayName("이미 처리된 이벤트는 스킵한다")
        @Test
        fun `skips already processed events`() {
            // given
            val likePayload = LikeEventPayload(productId = 1L, userId = 1L)
            val likeEnvelope = createEnvelope(
                id = "already-processed-event",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )

            val records = listOf(createConsumerRecord("like-events", likeEnvelope))

            every { eventHandledRepository.existsByIdempotencyKey("product-statistic:already-processed-event") } returns true
            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateLikeCount(any()) }
            verify(exactly = 0) { eventHandledRepository.save(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("배치 내 중복 이벤트 ID는 하나만 처리한다")
        @Test
        fun `processes only one event when batch contains duplicate event IDs`() {
            // given
            val likePayload = LikeEventPayload(productId = 1L, userId = 1L)
            val duplicateEnvelope1 = createEnvelope(
                id = "duplicate-event-id",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )
            val duplicateEnvelope2 = createEnvelope(
                id = "duplicate-event-id",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )

            val records = listOf(
                createConsumerRecord("like-events", duplicateEnvelope1),
                createConsumerRecord("like-events", duplicateEnvelope2),
            )

            every { eventHandledRepository.existsByIdempotencyKey(any()) } returns false
            every { eventHandledRepository.save(any()) } answers { firstArg() }
            every { productStatisticService.updateLikeCount(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateLikeCount(
                    match { command -> command.items.size == 1 },
                )
            }
            verify(exactly = 1) { eventHandledRepository.save(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("새 이벤트와 이미 처리된 이벤트가 섞인 경우 새 이벤트만 처리한다")
        @Test
        fun `processes only new events when mixed with already processed`() {
            // given
            val newPayload = LikeEventPayload(productId = 1L, userId = 1L)
            val newEnvelope = createEnvelope(
                id = "new-event",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(newPayload),
            )

            val processedPayload = LikeEventPayload(productId = 2L, userId = 2L)
            val processedEnvelope = createEnvelope(
                id = "processed-event",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "2",
                payload = objectMapper.writeValueAsString(processedPayload),
            )

            val records = listOf(
                createConsumerRecord("like-events", newEnvelope),
                createConsumerRecord("like-events", processedEnvelope),
            )

            every { eventHandledRepository.existsByIdempotencyKey("product-statistic:new-event") } returns false
            every { eventHandledRepository.existsByIdempotencyKey("product-statistic:processed-event") } returns true
            every { eventHandledRepository.save(any()) } answers { firstArg() }
            every { productStatisticService.updateLikeCount(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateLikeCount(
                    match { command ->
                        command.items.size == 1 && command.items[0].productId == 1L
                    },
                )
            }
            verify(exactly = 1) { eventHandledRepository.save(any()) }
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
                id = "unsupported-event",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "100",
                payload = "{}",
            )

            val records = listOf(createConsumerRecord("like-events", unsupportedEnvelope))

            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateLikeCount(any()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("지원하는 이벤트와 지원하지 않는 이벤트가 섞인 경우 지원하는 이벤트만 처리한다")
        @Test
        fun `processes only supported events when mixed with unsupported`() {
            // given
            val likePayload = LikeEventPayload(productId = 1L, userId = 1L)
            val likeEnvelope = createEnvelope(
                id = "like-event-1",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )

            val unsupportedEnvelope = createEnvelope(
                id = "unsupported-event",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "100",
                payload = "{}",
            )

            val records = listOf(
                createConsumerRecord("like-events", likeEnvelope),
                createConsumerRecord("like-events", unsupportedEnvelope),
            )

            every { eventHandledRepository.existsByIdempotencyKey(any()) } returns false
            every { eventHandledRepository.save(any()) } answers { firstArg() }
            every { productStatisticService.updateLikeCount(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                productStatisticService.updateLikeCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 1L
                    },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
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
                "like-events",
                0,
                0L,
                "key",
                "not a valid json",
            )

            val records = listOf(malformedRecord)

            // when & then
            assertThrows<BatchListenerFailedException> {
                productLikeEventConsumer.consume(records, acknowledgment)
            }
        }

        @DisplayName("첫 번째 메시지가 파싱 실패하면 나머지 메시지는 처리되지 않는다")
        @Test
        fun `does not process remaining messages when first message fails`() {
            // given
            val malformedRecord = ConsumerRecord(
                "like-events",
                0,
                0L,
                "key",
                "not a valid json",
            )

            val likePayload = LikeEventPayload(productId = 1L, userId = 1L)
            val validEnvelope = createEnvelope(
                id = "valid-event",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )
            val validRecord = createConsumerRecord("like-events", validEnvelope)

            val records = listOf(malformedRecord, validRecord)

            // when & then
            assertThrows<BatchListenerFailedException> {
                productLikeEventConsumer.consume(records, acknowledgment)
            }
            verify(exactly = 0) { productStatisticService.updateLikeCount(any()) }
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
