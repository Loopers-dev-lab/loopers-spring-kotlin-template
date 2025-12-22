package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.ProductCacheKeys
import com.loopers.cache.CacheTemplate
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.interfaces.consumer.product.event.StockDepletedEventPayload
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
 * ProductStockEventConsumer 통합 테스트
 *
 * Testcontainers Kafka를 사용하여 실제 메시지 처리를 검증합니다.
 *
 * 검증 범위:
 * - 메시지 수신 → 처리 → 캐시 무효화
 * - 지원하지 않는 이벤트 타입 필터링
 * - 실패 시 캐시 상태 유지
 *
 * Note: DLT 발행은 Consumer의 책임이 아닙니다.
 * Consumer는 실패 시 예외를 던지고, DLT 라우팅은 KafkaConfig의 ErrorHandler가 담당합니다.
 */
@SpringBootTest
@DisplayName("ProductStockEventConsumer 통합 테스트")
class ProductStockEventConsumerIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val cacheTemplate: CacheTemplate,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val TOPIC = "stock-events"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Test
    @DisplayName("재고 소진 이벤트 수신 시 캐시가 무효화된다")
    fun `invalidates cache when stock depleted event received`() {
        // given
        val productId = 100L
        val cacheKey = ProductCacheKeys.ProductDetail(productId)
        val cachedData = createCachedProductData(productId)
        cacheTemplate.put(cacheKey, cachedData)

        // verify cache exists before consumption
        val cacheBeforeConsume = cacheTemplate.get(
            cacheKey,
            object : TypeReference<Map<String, Any>>() {},
        )
        assertThat(cacheBeforeConsume).isNotNull

        val envelope = createStockDepletedEnvelope(
            eventId = "event-${UUID.randomUUID()}",
            productId = productId,
        )

        // when
        kafkaTemplate.send(TOPIC, "stock-1", objectMapper.writeValueAsString(envelope)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val cacheAfterConsume = cacheTemplate.get(
                cacheKey,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cacheAfterConsume).isNull()
        }
    }

    @Test
    @DisplayName("여러 재고 소진 이벤트 수신 시 각 상품의 캐시가 무효화된다")
    fun `invalidates multiple caches for batch events`() {
        // given
        val productId1 = 100L
        val productId2 = 200L
        val cacheKey1 = ProductCacheKeys.ProductDetail(productId1)
        val cacheKey2 = ProductCacheKeys.ProductDetail(productId2)

        cacheTemplate.put(cacheKey1, createCachedProductData(productId1))
        cacheTemplate.put(cacheKey2, createCachedProductData(productId2))

        // verify caches exist before consumption
        assertThat(cacheTemplate.get(cacheKey1, object : TypeReference<Map<String, Any>>() {})).isNotNull
        assertThat(cacheTemplate.get(cacheKey2, object : TypeReference<Map<String, Any>>() {})).isNotNull

        val envelope1 = createStockDepletedEnvelope(
            eventId = "event-1-${UUID.randomUUID()}",
            productId = productId1,
        )
        val envelope2 = createStockDepletedEnvelope(
            eventId = "event-2-${UUID.randomUUID()}",
            productId = productId2,
        )

        // when
        kafkaTemplate.send(TOPIC, "stock-1", objectMapper.writeValueAsString(envelope1)).get()
        kafkaTemplate.send(TOPIC, "stock-2", objectMapper.writeValueAsString(envelope2)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val cache1AfterConsume = cacheTemplate.get(cacheKey1, object : TypeReference<Map<String, Any>>() {})
            val cache2AfterConsume = cacheTemplate.get(cacheKey2, object : TypeReference<Map<String, Any>>() {})
            assertThat(cache1AfterConsume).isNull()
            assertThat(cache2AfterConsume).isNull()
        }
    }

    @Test
    @DisplayName("지원하지 않는 이벤트 타입은 무시하고 캐시가 유지된다")
    fun `ignores unsupported event types`() {
        // given
        val productId = 100L
        val cacheKey = ProductCacheKeys.ProductDetail(productId)
        val cachedData = createCachedProductData(productId)
        cacheTemplate.put(cacheKey, cachedData)

        val unsupportedEnvelope = CloudEventEnvelope(
            id = "event-1",
            type = "loopers.order.paid.v1",
            source = "test-source",
            aggregateType = "Order",
            aggregateId = "100",
            time = Instant.now(),
            payload = "{}",
        )

        // when
        kafkaTemplate.send(TOPIC, "order-1", objectMapper.writeValueAsString(unsupportedEnvelope)).get()

        // then
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val cacheAfterConsume = cacheTemplate.get(cacheKey, object : TypeReference<Map<String, Any>>() {})
            assertThat(cacheAfterConsume).isNotNull
        }
    }

    @Test
    @DisplayName("잘못된 JSON 포맷의 메시지는 기존 캐시에 영향을 주지 않는다")
    fun `malformed json does not affect existing cache`() {
        // given
        val productId = 100L
        val cacheKey = ProductCacheKeys.ProductDetail(productId)
        val cachedData = createCachedProductData(productId)
        cacheTemplate.put(cacheKey, cachedData)

        val malformedJson = """{"productId": 100, "broken": """

        // when
        kafkaTemplate.send(TOPIC, "key-1", malformedJson).get()

        // then
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val cacheAfterConsume = cacheTemplate.get(cacheKey, object : TypeReference<Map<String, Any>>() {})
            assertThat(cacheAfterConsume).isNotNull
        }
    }

    // ===========================================
    // Helper methods
    // ===========================================

    private fun createCachedProductData(productId: Long): Map<String, Any> = mapOf(
        "productId" to productId,
        "productName" to "Test Product",
        "price" to 10000L,
        "status" to "ON_SALE",
        "brandId" to 1L,
        "brandName" to "Test Brand",
        "stockQuantity" to 100,
        "likeCount" to 0L,
    )

    private fun createStockDepletedEnvelope(
        eventId: String,
        productId: Long,
    ): CloudEventEnvelope {
        val payload = StockDepletedEventPayload(productId = productId)
        return CloudEventEnvelope(
            id = eventId,
            type = "loopers.stock.depleted.v1",
            source = "test-source",
            aggregateType = "Stock",
            aggregateId = productId.toString(),
            time = Instant.now(),
            payload = objectMapper.writeValueAsString(payload),
        )
    }
}
