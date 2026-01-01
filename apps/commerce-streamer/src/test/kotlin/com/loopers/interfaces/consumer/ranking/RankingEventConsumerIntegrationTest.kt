package com.loopers.interfaces.consumer.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.ranking.ProductHourlyMetricJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
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
 * RankingEventConsumer 통합 테스트
 *
 * Testcontainers Kafka를 사용하여 실제 메시지 처리를 검증합니다.
 *
 * 검증 범위:
 * - 메시지 수신 -> 처리 -> DB 상태 변경
 * - 지원하지 않는 이벤트 타입 필터링
 * - 배치 처리
 * - 에러 핸들링
 */
@SpringBootTest
@DisplayName("RankingEventConsumer 통합 테스트")
class RankingEventConsumerIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val productHourlyMetricJpaRepository: ProductHourlyMetricJpaRepository,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val PRODUCT_TOPIC = "product-events"
        private const val LIKE_TOPIC = "like-events"
        private const val ORDER_TOPIC = "order-events"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Test
    @DisplayName("VIEW 이벤트 수신 시 조회수가 증가한다")
    fun `processes VIEW event and saves to DB`() {
        // given
        val envelope = createEnvelope(
            type = "loopers.product.viewed.v1",
            payload = """{"productId": 100, "userId": 1}""",
            aggregateType = "Product",
            aggregateId = "100",
        )

        // when
        kafkaTemplate.send(PRODUCT_TOPIC, "product-100", objectMapper.writeValueAsString(envelope)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].productId).isEqualTo(100L)
            assertThat(metrics[0].viewCount).isEqualTo(1L)
        }
    }

    @Test
    @DisplayName("LIKE_CREATED 이벤트 수신 시 좋아요 수가 증가한다")
    fun `processes LIKE_CREATED event and saves to DB`() {
        // given
        val envelope = createEnvelope(
            type = "loopers.like.created.v1",
            payload = """{"productId": 200, "userId": 1}""",
            aggregateType = "Like",
            aggregateId = "200",
        )

        // when
        kafkaTemplate.send(LIKE_TOPIC, "like-200", objectMapper.writeValueAsString(envelope)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].productId).isEqualTo(200L)
            assertThat(metrics[0].likeCount).isEqualTo(1L)
        }
    }

    @Test
    @DisplayName("LIKE_CANCELED 이벤트 수신 시 좋아요 수가 감소한다")
    fun `processes LIKE_CANCELED event and saves to DB`() {
        // given
        val envelope = createEnvelope(
            type = "loopers.like.canceled.v1",
            payload = """{"productId": 300, "userId": 1}""",
            aggregateType = "Like",
            aggregateId = "300",
        )

        // when
        kafkaTemplate.send(LIKE_TOPIC, "like-300", objectMapper.writeValueAsString(envelope)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].productId).isEqualTo(300L)
            assertThat(metrics[0].likeCount).isEqualTo(-1L)
        }
    }

    @Test
    @DisplayName("ORDER_PAID 이벤트 수신 시 주문 금액이 증가한다")
    fun `processes ORDER_PAID event and saves to DB`() {
        // given
        val payload = """{
            "orderId": 1,
            "userId": 1,
            "totalAmount": 30000,
            "orderItems": [
                {"productId": 100, "quantity": 2, "unitPrice": 10000}
            ]
        }"""
        val envelope = createEnvelope(
            type = "loopers.order.paid.v1",
            payload = payload,
            aggregateType = "Order",
            aggregateId = "1",
        )

        // when
        kafkaTemplate.send(ORDER_TOPIC, "order-1", objectMapper.writeValueAsString(envelope)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].productId).isEqualTo(100L)
            assertThat(metrics[0].orderAmount).isEqualByComparingTo(java.math.BigDecimal("20000")) // 10000 * 2
        }
    }

    @Test
    @DisplayName("지원하지 않는 이벤트 타입은 무시한다")
    fun `ignores unsupported event types`() {
        // given
        val unsupportedEnvelope = createEnvelope(
            type = "loopers.unknown.event.v1",
            payload = """{"productId": 100}""",
            aggregateType = "Unknown",
            aggregateId = "100",
        )

        // when
        kafkaTemplate.send(PRODUCT_TOPIC, "key-1", objectMapper.writeValueAsString(unsupportedEnvelope)).get()

        // then
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(3)).untilAsserted {
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).isEmpty()
        }
    }

    @Test
    @DisplayName("여러 이벤트를 배치로 처리한다")
    fun `processes multiple events in single batch`() {
        // given
        val envelope1 = createEnvelope(
            type = "loopers.product.viewed.v1",
            payload = """{"productId": 100, "userId": 1}""",
            aggregateType = "Product",
            aggregateId = "100",
        )
        val envelope2 = createEnvelope(
            type = "loopers.product.viewed.v1",
            payload = """{"productId": 100, "userId": 2}""",
            aggregateType = "Product",
            aggregateId = "100",
        )
        val envelope3 = createEnvelope(
            type = "loopers.like.created.v1",
            payload = """{"productId": 200, "userId": 1}""",
            aggregateType = "Like",
            aggregateId = "200",
        )

        // when
        kafkaTemplate.send(PRODUCT_TOPIC, "product-100", objectMapper.writeValueAsString(envelope1)).get()
        kafkaTemplate.send(PRODUCT_TOPIC, "product-100", objectMapper.writeValueAsString(envelope2)).get()
        kafkaTemplate.send(LIKE_TOPIC, "like-200", objectMapper.writeValueAsString(envelope3)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(2)

            val product100Metric = metrics.find { it.productId == 100L }
            assertThat(product100Metric).isNotNull
            assertThat(product100Metric!!.viewCount).isEqualTo(2L)

            val product200Metric = metrics.find { it.productId == 200L }
            assertThat(product200Metric).isNotNull
            assertThat(product200Metric!!.likeCount).isEqualTo(1L)
        }
    }

    @Test
    @DisplayName("잘못된 형식의 이벤트는 건너뛰고 유효한 이벤트만 처리한다")
    fun `skips malformed events and processes valid ones`() {
        // given
        val validEnvelope = createEnvelope(
            type = "loopers.product.viewed.v1",
            payload = """{"productId": 100, "userId": 1}""",
            aggregateType = "Product",
            aggregateId = "100",
        )
        val malformedJson = """{"productId": 100, "broken": """

        // when
        kafkaTemplate.send(PRODUCT_TOPIC, "key-1", malformedJson).get()
        kafkaTemplate.send(PRODUCT_TOPIC, "key-2", objectMapper.writeValueAsString(validEnvelope)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val metrics = productHourlyMetricJpaRepository.findAll()
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].productId).isEqualTo(100L)
            assertThat(metrics[0].viewCount).isEqualTo(1L)
        }
    }

    // ===========================================
    // Helper methods
    // ===========================================

    private fun createEnvelope(
        id: String = "evt-${UUID.randomUUID()}",
        type: String,
        payload: String,
        aggregateType: String,
        aggregateId: String,
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
}
