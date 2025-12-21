package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.domain.product.ProductStatisticService
import com.loopers.domain.product.UpdateLikeCountCommand
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.interfaces.consumer.product.event.LikeEventPayload
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
import org.springframework.kafka.support.Acknowledgment
import java.time.Instant

@DisplayName("ProductLikeEventConsumer 순차 처리 단위 테스트")
class ProductLikeEventConsumerTest {

    private lateinit var productStatisticService: ProductStatisticService
    private lateinit var productEventMapper: ProductEventMapper
    private lateinit var eventHandledService: EventHandledService
    private lateinit var objectMapper: ObjectMapper
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var productLikeEventConsumer: ProductLikeEventConsumer

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

        productLikeEventConsumer = ProductLikeEventConsumer(
            productStatisticService,
            productEventMapper,
            eventHandledService,
            objectMapper,
        )
    }

    @DisplayName("Like 이벤트 처리")
    @Nested
    inner class HandleLikeEvents {

        @DisplayName("Like 이벤트를 레코드 단위로 처리하여 updateLikeCount()를 각각 호출한다")
        @Test
        fun `calls updateLikeCount per record for like events`() {
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

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { eventHandledService.markAsHandled(any()) } just runs
            every { productStatisticService.updateLikeCount(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 2) { productStatisticService.updateLikeCount(any()) }
            verify(exactly = 1) {
                productStatisticService.updateLikeCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 1L &&
                            command.items[0].type == UpdateLikeCountCommand.LikeType.CREATED
                    },
                )
            }
            verify(exactly = 1) {
                productStatisticService.updateLikeCount(
                    match { command ->
                        command.items.size == 1 &&
                            command.items[0].productId == 2L &&
                            command.items[0].type == UpdateLikeCountCommand.LikeType.CANCELED
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

            every { eventHandledService.isAlreadyHandled("product-statistic:already-processed-event") } returns true
            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { productStatisticService.updateLikeCount(any()) }
            verify(exactly = 0) { eventHandledService.markAsHandled(any()) }
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

            every { eventHandledService.isAlreadyHandled("product-statistic:new-event") } returns false
            every { eventHandledService.isAlreadyHandled("product-statistic:processed-event") } returns true
            every { eventHandledService.markAsHandled("product-statistic:new-event") } just runs
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
            verify(exactly = 1) { eventHandledService.markAsHandled("product-statistic:new-event") }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("처리 성공 후 멱등성 키를 저장한다")
        @Test
        fun `marks event as handled after successful processing`() {
            // given
            val likePayload = LikeEventPayload(productId = 1L, userId = 1L)
            val likeEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )

            val records = listOf(createConsumerRecord("like-events", likeEnvelope))

            every { eventHandledService.isAlreadyHandled("product-statistic:event-1") } returns false
            every { eventHandledService.markAsHandled("product-statistic:event-1") } just runs
            every { productStatisticService.updateLikeCount(any()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) { eventHandledService.markAsHandled("product-statistic:event-1") }
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
            verify(exactly = 0) { eventHandledService.isAlreadyHandled(any()) }
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

            every { eventHandledService.isAlreadyHandled(any()) } returns false
            every { eventHandledService.markAsHandled(any()) } just runs
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
