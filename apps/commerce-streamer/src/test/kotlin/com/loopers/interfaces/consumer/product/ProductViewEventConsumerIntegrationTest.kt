package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository
import com.loopers.infrastructure.product.ProductStatisticJpaRepository
import com.loopers.interfaces.consumer.product.event.ProductViewedEventPayload
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
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import java.time.Instant

@SpringBootTest
@DisplayName("ProductViewEventConsumer 통합 테스트")
class ProductViewEventConsumerIntegrationTest @Autowired constructor(
    private val productViewEventConsumer: ProductViewEventConsumer,
    private val productStatisticRepository: ProductStatisticRepository,
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

    @DisplayName("조회수 증가 테스트")
    @Nested
    inner class ViewCountIncrement {

        @DisplayName("상품 조회 이벤트 소비 시 조회수가 증가한다")
        @Test
        fun `increases view count when product viewed event is consumed`() {
            // given
            val productId = 100L
            val initialViewCount = 10L
            val productStatistic = createProductStatistic(productId = productId, viewCount = initialViewCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val viewedPayload = createProductViewedPayload(productId = productId, userId = 1L)
            val envelope = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = productId.toString(),
                payload = objectMapper.writeValueAsString(viewedPayload),
            )
            val records = listOf(createConsumerRecord("product-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.viewCount).isEqualTo(11)
        }

        @DisplayName("배치로 여러 이벤트 소비 시 각 상품의 조회수가 올바르게 증가한다")
        @Test
        fun `increases view count correctly for multiple events in batch`() {
            // given
            val productStatistic1 = createProductStatistic(productId = 100L, viewCount = 10L)
            val productStatistic2 = createProductStatistic(productId = 200L, viewCount = 20L)
            productStatisticJpaRepository.saveAllAndFlush(listOf(productStatistic1, productStatistic2))

            val viewedPayload1 = createProductViewedPayload(productId = 100L, userId = 1L)
            val envelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "100",
                payload = objectMapper.writeValueAsString(viewedPayload1),
            )

            val viewedPayload2 = createProductViewedPayload(productId = 200L, userId = 2L)
            val envelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = "200",
                payload = objectMapper.writeValueAsString(viewedPayload2),
            )

            val records = listOf(
                createConsumerRecord("product-events", envelope1),
                createConsumerRecord("product-events", envelope2),
            )
            val acknowledgment = createMockAcknowledgment()

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            val result1 = productStatisticJpaRepository.findByProductId(100L)
            val result2 = productStatisticJpaRepository.findByProductId(200L)

            assertThat(result1).isNotNull
            assertThat(result1!!.viewCount).isEqualTo(11)
            assertThat(result2).isNotNull
            assertThat(result2!!.viewCount).isEqualTo(21)
        }

        @DisplayName("빈 배치 소비 시 조회수가 변경되지 않는다")
        @Test
        fun `view count unchanged for empty batch`() {
            // given
            val productId = 100L
            val initialViewCount = 10L
            val productStatistic = createProductStatistic(productId = productId, viewCount = initialViewCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val records = emptyList<ConsumerRecord<String, String>>()
            val acknowledgment = createMockAcknowledgment()

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.viewCount).isEqualTo(initialViewCount)
        }

        @DisplayName("지원하지 않는 이벤트 타입 소비 시 조회수가 변경되지 않는다")
        @Test
        fun `view count unchanged for unsupported event types`() {
            // given
            val productId = 100L
            val initialViewCount = 10L
            val productStatistic = createProductStatistic(productId = productId, viewCount = initialViewCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val unsupportedEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "100",
                payload = "{}",
            )
            val records = listOf(createConsumerRecord("product-events", unsupportedEnvelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.viewCount).isEqualTo(initialViewCount)
        }
    }

    @DisplayName("멱등성 테스트")
    @Nested
    inner class IdempotencyCheck {

        @DisplayName("이미 처리된 이벤트 소비 시 조회수가 변경되지 않는다")
        @Test
        fun `view count unchanged when event already processed`() {
            // given
            val productId = 100L
            val initialViewCount = 10L
            val productStatistic = createProductStatistic(productId = productId, viewCount = initialViewCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val eventId = "event-already-processed"
            val idempotencyKey = "product-statistic:$eventId"
            eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = idempotencyKey))

            val viewedPayload = createProductViewedPayload(productId = productId, userId = 1L)
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = productId.toString(),
                payload = objectMapper.writeValueAsString(viewedPayload),
            )
            val records = listOf(createConsumerRecord("product-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.viewCount).isEqualTo(initialViewCount)
        }

        @DisplayName("이벤트 처리 후 멱등성 키가 DB에 저장된다")
        @Test
        fun `idempotency key exists in DB after processing`() {
            // given
            val productId = 100L
            val productStatistic = createProductStatistic(productId = productId, viewCount = 0L)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val eventId = "new-event-id"
            val viewedPayload = createProductViewedPayload(productId = productId, userId = 1L)
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.product.viewed.v1",
                aggregateType = "Product",
                aggregateId = productId.toString(),
                payload = objectMapper.writeValueAsString(viewedPayload),
            )
            val records = listOf(createConsumerRecord("product-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productViewEventConsumer.consume(records, acknowledgment)

            // then
            val expectedIdempotencyKey = "product-statistic:$eventId"
            val exists = eventHandledRepository.existsByIdempotencyKey(expectedIdempotencyKey)
            assertThat(exists).isTrue()
        }
    }

    @DisplayName("파싱 실패 처리 테스트")
    @Nested
    inner class ParsingFailure {

        @DisplayName("잘못된 형식의 메시지 소비 시 BatchListenerFailedException이 발생한다")
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
            val acknowledgment = createMockAcknowledgment()

            // when & then
            assertThrows<BatchListenerFailedException> {
                productViewEventConsumer.consume(records, acknowledgment)
            }
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

    private fun createProductViewedPayload(
        productId: Long,
        userId: Long,
    ): ProductViewedEventPayload = ProductViewedEventPayload(
        productId = productId,
        userId = userId,
    )

    private fun createMockAcknowledgment(): Acknowledgment {
        val ack = mockk<Acknowledgment>()
        every { ack.acknowledge() } just runs
        return ack
    }
}
