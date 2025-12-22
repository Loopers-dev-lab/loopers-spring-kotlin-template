package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.product.ProductStatistic
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.product.ProductStatisticJpaRepository
import com.loopers.interfaces.consumer.product.event.ProductViewedEventPayload
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
 * ProductViewEventConsumer 통합 테스트
 *
 * Testcontainers Kafka를 사용하여 실제 메시지 처리를 검증합니다.
 *
 * 검증 범위:
 * - 메시지 수신 → 처리 → DB 상태 변경
 * - 지원하지 않는 이벤트 타입 필터링
 * - 실패 시 데이터 안전성
 *
 * Note: DLT 발행은 Consumer의 책임이 아닙니다.
 * Consumer는 실패 시 예외를 던지고, DLT 라우팅은 KafkaConfig의 ErrorHandler가 담당합니다.
 */
@SpringBootTest
@DisplayName("ProductViewEventConsumer 통합 테스트")
class ProductViewEventConsumerIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val productStatisticJpaRepository: ProductStatisticJpaRepository,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val TOPIC = "product-events"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    @DisplayName("상품 조회 이벤트 수신 시 조회수가 증가한다")
    fun `increases view count when product viewed event received`() {
        // given
        val productId = 100L
        val initialViewCount = 10L
        saveProductStatistic(productId = productId, viewCount = initialViewCount)

        val envelope = createProductViewedEnvelope(
            eventId = "event-${UUID.randomUUID()}",
            productId = productId,
            userId = 1L,
        )

        // when
        kafkaTemplate.send(TOPIC, "product-1", objectMapper.writeValueAsString(envelope)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.viewCount).isEqualTo(11)
        }
    }

    @Test
    @DisplayName("배치로 여러 이벤트 수신 시 각 상품의 조회수가 올바르게 증가한다")
    fun `increases view count correctly for multiple events in batch`() {
        // given
        saveProductStatistic(productId = 100L, viewCount = 10L)
        saveProductStatistic(productId = 200L, viewCount = 20L)

        val envelope1 = createProductViewedEnvelope(
            eventId = "event-1-${UUID.randomUUID()}",
            productId = 100L,
            userId = 1L,
        )
        val envelope2 = createProductViewedEnvelope(
            eventId = "event-2-${UUID.randomUUID()}",
            productId = 200L,
            userId = 2L,
        )

        // when
        kafkaTemplate.send(TOPIC, "product-1", objectMapper.writeValueAsString(envelope1)).get()
        kafkaTemplate.send(TOPIC, "product-2", objectMapper.writeValueAsString(envelope2)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val result1 = productStatisticJpaRepository.findByProductId(100L)
            val result2 = productStatisticJpaRepository.findByProductId(200L)

            assertThat(result1).isNotNull
            assertThat(result1!!.viewCount).isEqualTo(11)
            assertThat(result2).isNotNull
            assertThat(result2!!.viewCount).isEqualTo(21)
        }
    }

    @Test
    @DisplayName("지원하지 않는 이벤트 타입은 무시하고 조회수가 변경되지 않는다")
    fun `ignores unsupported event types`() {
        // given
        val productId = 100L
        val initialViewCount = 10L
        saveProductStatistic(productId = productId, viewCount = initialViewCount)

        val unsupportedEnvelope = CloudEventEnvelope(
            id = "event-1",
            type = "loopers.like.created.v1",
            source = "test-source",
            aggregateType = "Like",
            aggregateId = "100",
            time = Instant.now(),
            payload = "{}",
        )

        // when
        kafkaTemplate.send(TOPIC, "like-1", objectMapper.writeValueAsString(unsupportedEnvelope)).get()

        // then
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result!!.viewCount).isEqualTo(initialViewCount)
        }
    }

    @Test
    @DisplayName("잘못된 JSON 포맷의 메시지는 기존 데이터에 영향을 주지 않는다")
    fun `malformed json does not affect existing data`() {
        // given
        val initialViewCount = 10L
        saveProductStatistic(productId = 100L, viewCount = initialViewCount)
        val malformedJson = """{"productId": 100, "broken": """

        // when
        kafkaTemplate.send(TOPIC, "key-1", malformedJson).get()

        // then
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(100L)
            assertThat(result!!.viewCount).isEqualTo(initialViewCount)
        }
    }

    // ===========================================
    // Helper methods
    // ===========================================

    private fun saveProductStatistic(
        productId: Long,
        viewCount: Long = 0,
        likeCount: Long = 0,
        salesCount: Long = 0,
    ): ProductStatistic = productStatisticJpaRepository.saveAndFlush(
        ProductStatistic(
            productId = productId,
            viewCount = viewCount,
            likeCount = likeCount,
            salesCount = salesCount,
        ),
    )

    private fun createProductViewedEnvelope(
        eventId: String,
        productId: Long,
        userId: Long,
    ): CloudEventEnvelope {
        val payload = ProductViewedEventPayload(productId = productId, userId = userId)
        return CloudEventEnvelope(
            id = eventId,
            type = "loopers.product.viewed.v1",
            source = "test-source",
            aggregateType = "Product",
            aggregateId = productId.toString(),
            time = Instant.now(),
            payload = objectMapper.writeValueAsString(payload),
        )
    }
}
