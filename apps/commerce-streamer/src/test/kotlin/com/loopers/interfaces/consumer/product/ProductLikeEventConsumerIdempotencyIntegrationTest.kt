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
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * ProductLikeEventConsumer 멱등성 통합 테스트
 *
 * 중복 메시지 처리 시 멱등성 보장을 검증합니다.
 *
 * 검증 범위:
 * - 동일한 메시지 중복 수신 → 한 번만 처리
 * - 이미 처리된 이벤트 → 무시
 * - 멱등성 키 저장 확인
 */
@SpringBootTest
@DisplayName("ProductLikeEventConsumer 멱등성 테스트")
class ProductLikeEventConsumerIdempotencyIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val productStatisticJpaRepository: ProductStatisticJpaRepository,
    private val eventHandledRepository: EventHandledRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val TOPIC = "like-events"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    @DisplayName("동일한 메시지가 중복 도착해도 좋아요 수는 한 번만 증가한다")
    fun `increases like count only once when duplicate messages arrive`() {
        // given
        val productId = 100L
        val initialLikeCount = 10L
        saveProductStatistic(productId = productId, likeCount = initialLikeCount)

        val eventId = "event-duplicate-${UUID.randomUUID()}"
        val envelope = createLikeEnvelope(
            eventId = eventId,
            type = "loopers.like.created.v1",
            aggregateId = "1",
            productId = productId,
            userId = 1L,
        )
        val messageJson = objectMapper.writeValueAsString(envelope)

        // when - 동일한 메시지 3번 전송
        repeat(3) { kafkaTemplate.send(TOPIC, "like-1", messageJson).get() }

        // then - 처리 완료 대기
        val expectedCount = initialLikeCount + 1
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result!!.likeCount).isEqualTo(expectedCount)
        }

        // then - 추가 시간 동안 값 변경 없음 확인 (멱등성 검증)
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result!!.likeCount).isEqualTo(expectedCount)
        }
    }

    @Test
    @DisplayName("이미 처리된 이벤트 수신 시 좋아요 수가 변경되지 않는다")
    fun `ignores already processed event`() {
        // given
        val productId = 100L
        val initialLikeCount = 10L
        saveProductStatistic(productId = productId, likeCount = initialLikeCount)

        val eventId = "event-already-processed"
        val idempotencyKey = "product-statistic:$eventId"
        eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = idempotencyKey))

        val envelope = createLikeEnvelope(
            eventId = eventId,
            type = "loopers.like.created.v1",
            aggregateId = "1",
            productId = productId,
            userId = 1L,
        )

        // when
        kafkaTemplate.send(TOPIC, "like-1", objectMapper.writeValueAsString(envelope)).get()

        // then
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result!!.likeCount).isEqualTo(initialLikeCount)
        }
    }

    @Test
    @DisplayName("이벤트 처리 후 멱등성 키가 DB에 저장된다")
    fun `persists idempotency key after processing`() {
        // given
        val productId = 100L
        saveProductStatistic(productId = productId, likeCount = 0L)

        val eventId = "new-event-${UUID.randomUUID()}"
        val envelope = createLikeEnvelope(
            eventId = eventId,
            type = "loopers.like.created.v1",
            aggregateId = "1",
            productId = productId,
            userId = 1L,
        )

        // when
        kafkaTemplate.send(TOPIC, "like-1", objectMapper.writeValueAsString(envelope)).get()

        // then
        val expectedIdempotencyKey = "product-statistic:$eventId"
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val exists = eventHandledRepository.existsByIdempotencyKey(expectedIdempotencyKey)
            assertThat(exists).isTrue()
        }
    }

    // ===========================================
    // Helper methods
    // ===========================================

    private fun saveProductStatistic(
        productId: Long,
        likeCount: Long = 0,
    ): ProductStatistic = productStatisticJpaRepository.saveAndFlush(
        ProductStatistic(
            productId = productId,
            viewCount = 0,
            likeCount = likeCount,
            salesCount = 0,
        ),
    )

    private fun createLikeEnvelope(
        eventId: String,
        type: String,
        aggregateId: String,
        productId: Long,
        userId: Long,
    ): CloudEventEnvelope {
        val payload = LikeEventPayload(productId = productId, userId = userId)
        return CloudEventEnvelope(
            id = eventId,
            type = type,
            source = "test-source",
            aggregateType = "Like",
            aggregateId = aggregateId,
            time = Instant.now(),
            payload = objectMapper.writeValueAsString(payload),
        )
    }
}
