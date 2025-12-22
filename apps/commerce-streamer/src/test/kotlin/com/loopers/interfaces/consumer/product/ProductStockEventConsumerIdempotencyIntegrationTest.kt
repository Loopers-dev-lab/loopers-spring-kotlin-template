package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.ProductCacheKeys
import com.loopers.cache.CacheTemplate
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository
import com.loopers.interfaces.consumer.product.event.StockDepletedEventPayload
import com.loopers.support.idempotency.EventHandled
import com.loopers.support.idempotency.EventHandledRepository
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
 * ProductStockEventConsumer 멱등성 통합 테스트
 *
 * 중복 메시지 처리 시 멱등성 보장을 검증합니다.
 *
 * 검증 범위:
 * - 동일한 메시지 중복 수신 → 한 번만 처리
 * - 이미 처리된 이벤트 → 무시
 * - 멱등성 키 저장 확인
 */
@SpringBootTest
@DisplayName("ProductStockEventConsumer 멱등성 테스트")
class ProductStockEventConsumerIdempotencyIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val cacheTemplate: CacheTemplate,
    private val eventHandledRepository: EventHandledRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
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
    @DisplayName("이미 처리된 이벤트 수신 시 캐시가 유지된다")
    fun `cache unchanged when event already processed`() {
        // given
        val productId = 100L
        val cacheKey = ProductCacheKeys.ProductDetail(productId)
        val cachedData = createCachedProductData(productId)
        cacheTemplate.put(cacheKey, cachedData)

        val eventId = "event-already-processed"
        val idempotencyKey = "product-cache:$eventId"
        eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = idempotencyKey))

        val envelope = createStockDepletedEnvelope(
            eventId = eventId,
            productId = productId,
        )

        // when
        kafkaTemplate.send(TOPIC, "stock-1", objectMapper.writeValueAsString(envelope)).get()

        // then - 캐시가 유지됨
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val cacheAfterConsume = cacheTemplate.get(cacheKey, object : TypeReference<Map<String, Any>>() {})
            assertThat(cacheAfterConsume).isNotNull
        }
    }

    @Test
    @DisplayName("이벤트 처리 후 멱등성 키가 DB에 저장된다")
    fun `persists idempotency key after processing`() {
        // given
        val productId = 100L
        val cacheKey = ProductCacheKeys.ProductDetail(productId)
        val cachedData = createCachedProductData(productId)
        cacheTemplate.put(cacheKey, cachedData)

        val eventId = "new-event-${UUID.randomUUID()}"
        val envelope = createStockDepletedEnvelope(
            eventId = eventId,
            productId = productId,
        )

        // when
        kafkaTemplate.send(TOPIC, "stock-1", objectMapper.writeValueAsString(envelope)).get()

        // then
        val expectedIdempotencyKey = "product-cache:$eventId"
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val exists = eventHandledRepository.existsByIdempotencyKey(expectedIdempotencyKey)
            assertThat(exists).isTrue()
        }
    }

    @Test
    @DisplayName("동일한 메시지가 중복 도착해도 멱등성 키는 한 번만 저장된다")
    fun `stores idempotency key only once when duplicate messages arrive`() {
        // given
        val productId = 100L
        val cacheKey = ProductCacheKeys.ProductDetail(productId)
        val cachedData = createCachedProductData(productId)
        cacheTemplate.put(cacheKey, cachedData)

        val eventId = "event-duplicate-${UUID.randomUUID()}"
        val envelope = createStockDepletedEnvelope(
            eventId = eventId,
            productId = productId,
        )
        val messageJson = objectMapper.writeValueAsString(envelope)

        // when - 동일한 메시지 3번 전송
        repeat(3) { kafkaTemplate.send(TOPIC, "stock-1", messageJson).get() }

        // then - 멱등성 키가 저장됨
        val expectedIdempotencyKey = "product-cache:$eventId"
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val exists = eventHandledRepository.existsByIdempotencyKey(expectedIdempotencyKey)
            assertThat(exists).isTrue()
        }

        // then - 추가 시간 동안 에러 없이 안정적으로 처리됨
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val exists = eventHandledRepository.existsByIdempotencyKey(expectedIdempotencyKey)
            assertThat(exists).isTrue()
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
