package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.product.ProductStatistic
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository
import com.loopers.infrastructure.product.ProductStatisticJpaRepository
import com.loopers.interfaces.consumer.product.event.LikeEventPayload
import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
import com.loopers.utils.DatabaseCleanUp
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.support.Acknowledgment
import java.time.Instant

@SpringBootTest
@DisplayName("ProductLikeEventConsumer 통합 테스트")
class ProductLikeEventConsumerIntegrationTest @Autowired constructor(
    private val productLikeEventConsumer: ProductLikeEventConsumer,
    private val productStatisticJpaRepository: ProductStatisticJpaRepository,
    private val eventHandledRepository: EventHandledRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("좋아요 수 변경 테스트")
    @Nested
    inner class LikeCountChange {

        @DisplayName("좋아요 생성 이벤트 소비 시 좋아요 수가 증가한다")
        @Test
        fun `increases like count when like created event is consumed`() {
            // given
            val productId = 100L
            val initialLikeCount = 10L
            val productStatistic = createProductStatistic(productId = productId, likeCount = initialLikeCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val likePayload = createLikeEventPayload(productId = productId, userId = 1L)
            val envelope = createEnvelope(
                id = "event-1",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )
            val records = listOf(createConsumerRecord("like-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(11)
        }

        @DisplayName("좋아요 취소 이벤트 소비 시 좋아요 수가 감소한다")
        @Test
        fun `decreases like count when like canceled event is consumed`() {
            // given
            val productId = 100L
            val initialLikeCount = 10L
            val productStatistic = createProductStatistic(productId = productId, likeCount = initialLikeCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val likePayload = createLikeEventPayload(productId = productId, userId = 1L)
            val envelope = createEnvelope(
                id = "event-1",
                type = "loopers.like.canceled.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )
            val records = listOf(createConsumerRecord("like-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(9)
        }

        @DisplayName("배치로 여러 이벤트 소비 시 각 레코드마다 좋아요 수가 올바르게 변경된다")
        @Test
        fun `processes multiple like events correctly per record`() {
            // given
            val productStatistic1 = createProductStatistic(productId = 100L, likeCount = 10L)
            val productStatistic2 = createProductStatistic(productId = 200L, likeCount = 20L)
            productStatisticJpaRepository.saveAllAndFlush(listOf(productStatistic1, productStatistic2))

            val likePayload1 = createLikeEventPayload(productId = 100L, userId = 1L)
            val envelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload1),
            )

            val likePayload2 = createLikeEventPayload(productId = 200L, userId = 2L)
            val envelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.like.canceled.v1",
                aggregateType = "Like",
                aggregateId = "2",
                payload = objectMapper.writeValueAsString(likePayload2),
            )

            val records = listOf(
                createConsumerRecord("like-events", envelope1),
                createConsumerRecord("like-events", envelope2),
            )
            val acknowledgment = createMockAcknowledgment()

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            val result1 = productStatisticJpaRepository.findByProductId(100L)
            val result2 = productStatisticJpaRepository.findByProductId(200L)

            assertThat(result1).isNotNull
            assertThat(result1!!.likeCount).isEqualTo(11)
            assertThat(result2).isNotNull
            assertThat(result2!!.likeCount).isEqualTo(19)
        }

        @DisplayName("빈 배치 소비 시 좋아요 수가 변경되지 않는다")
        @Test
        fun `like count unchanged for empty batch`() {
            // given
            val productId = 100L
            val initialLikeCount = 10L
            val productStatistic = createProductStatistic(productId = productId, likeCount = initialLikeCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val records = emptyList<ConsumerRecord<String, String>>()
            val acknowledgment = createMockAcknowledgment()

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(initialLikeCount)
        }
    }

    @DisplayName("멱등성 테스트")
    @Nested
    inner class IdempotencyCheck {

        @DisplayName("이미 처리된 이벤트 소비 시 좋아요 수가 변경되지 않는다")
        @Test
        fun `like count unchanged when event already processed`() {
            // given
            val productId = 100L
            val initialLikeCount = 10L
            val productStatistic = createProductStatistic(productId = productId, likeCount = initialLikeCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val eventId = "event-already-processed"
            val idempotencyKey = "product-statistic:$eventId"
            eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = idempotencyKey))

            val likePayload = createLikeEventPayload(productId = productId, userId = 1L)
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )
            val records = listOf(createConsumerRecord("like-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(initialLikeCount)
        }

        @DisplayName("이벤트 처리 후 멱등성 키가 DB에 저장된다")
        @Test
        fun `idempotency key exists in DB after processing`() {
            // given
            val productId = 100L
            val productStatistic = createProductStatistic(productId = productId, likeCount = 0L)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val eventId = "new-event-id"
            val likePayload = createLikeEventPayload(productId = productId, userId = 1L)
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(likePayload),
            )
            val records = listOf(createConsumerRecord("like-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            val expectedIdempotencyKey = "product-statistic:$eventId"
            val exists = eventHandledRepository.existsByIdempotencyKey(expectedIdempotencyKey)
            assertThat(exists).isTrue()
        }
    }

    @DisplayName("지원하지 않는 이벤트 필터링")
    @Nested
    inner class UnsupportedEventFilter {

        @DisplayName("지원하지 않는 이벤트 타입 소비 시 좋아요 수가 변경되지 않는다")
        @Test
        fun `silently skips unsupported event types`() {
            // given
            val productId = 100L
            val initialLikeCount = 10L
            val productStatistic = createProductStatistic(productId = productId, likeCount = initialLikeCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val unsupportedEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "100",
                payload = "{}",
            )
            val records = listOf(createConsumerRecord("like-events", unsupportedEnvelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productLikeEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.likeCount).isEqualTo(initialLikeCount)
        }
    }

    private fun createProductStatistic(
        productId: Long,
        viewCount: Long = 0,
        likeCount: Long = 0,
        salesCount: Long = 0,
    ): ProductStatistic = ProductStatistic(
        productId = productId,
        viewCount = viewCount,
        likeCount = likeCount,
        salesCount = salesCount,
    )

    private fun createEnvelope(
        id: String = "test-event-id",
        type: String,
        aggregateType: String,
        aggregateId: String,
        payload: String,
        time: Instant = Instant.now(),
    ): CloudEventEnvelope = CloudEventEnvelope(
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

    private fun createLikeEventPayload(
        productId: Long,
        userId: Long,
    ): LikeEventPayload = LikeEventPayload(
        productId = productId,
        userId = userId,
    )

    private fun createMockAcknowledgment(): Acknowledgment {
        val ack = mockk<Acknowledgment>()
        every { ack.acknowledge() } just runs
        return ack
    }
}
