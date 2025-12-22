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
@DisplayName("ProductStockEventConsumer 통합 테스트")
class ProductStockEventConsumerIntegrationTest @Autowired constructor(
    private val productStockEventConsumer: ProductStockEventConsumer,
    private val cacheTemplate: CacheTemplate,
    private val eventHandledRepository: EventHandledRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("캐시 무효화 테스트")
    @Nested
    inner class CacheInvalidation {

        @DisplayName("재고 소진 이벤트 소비 시 캐시가 무효화된다")
        @Test
        fun `cache invalidated when stock depleted event is consumed`() {
            // given
            val productId = 100L
            val cacheKey = ProductCacheKeys.ProductDetail(productId)
            val cachedData = createCachedProductData(productId)
            cacheTemplate.put(cacheKey, cachedData)

            // given - verify cache exists before consumption
            val cacheBeforeConsume = cacheTemplate.get(
                cacheKey,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cacheBeforeConsume).isNotNull

            val stockDepletedPayload = StockDepletedEventPayload(productId = productId)
            val envelope = createEnvelope(
                id = "event-1",
                type = "loopers.stock.depleted.v1",
                aggregateType = "Stock",
                aggregateId = productId.toString(),
                payload = objectMapper.writeValueAsString(stockDepletedPayload),
            )
            val records = listOf(createConsumerRecord("stock-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then
            val cacheAfterConsume = cacheTemplate.get(
                cacheKey,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cacheAfterConsume).isNull()
        }

        @DisplayName("빈 배치 소비 시 캐시가 변경되지 않는다")
        @Test
        fun `cache unchanged for empty batch`() {
            // given
            val productId = 100L
            val cacheKey = ProductCacheKeys.ProductDetail(productId)
            val cachedData = createCachedProductData(productId)
            cacheTemplate.put(cacheKey, cachedData)

            val records = emptyList<ConsumerRecord<String, String>>()
            val acknowledgment = createMockAcknowledgment()

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then
            val cacheAfterConsume = cacheTemplate.get(
                cacheKey,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cacheAfterConsume).isNotNull
        }
    }

    @DisplayName("멱등성 테스트")
    @Nested
    inner class IdempotencyCheck {

        @DisplayName("이미 처리된 이벤트 소비 시 캐시가 변경되지 않는다")
        @Test
        fun `cache unchanged when event already processed`() {
            // given
            val productId = 100L
            val cacheKey = ProductCacheKeys.ProductDetail(productId)
            val cachedData = createCachedProductData(productId)
            cacheTemplate.put(cacheKey, cachedData)

            val eventId = "event-already-processed"
            val idempotencyKey = "product-cache:$eventId"
            eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = idempotencyKey))

            val stockDepletedPayload = StockDepletedEventPayload(productId = productId)
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.stock.depleted.v1",
                aggregateType = "Stock",
                aggregateId = productId.toString(),
                payload = objectMapper.writeValueAsString(stockDepletedPayload),
            )
            val records = listOf(createConsumerRecord("stock-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then
            val cacheAfterConsume = cacheTemplate.get(
                cacheKey,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cacheAfterConsume).isNotNull
        }

        @DisplayName("이벤트 처리 후 멱등성 키가 DB에 저장된다")
        @Test
        fun `idempotency key exists in DB after processing`() {
            // given
            val productId = 100L
            val cacheKey = ProductCacheKeys.ProductDetail(productId)
            val cachedData = createCachedProductData(productId)
            cacheTemplate.put(cacheKey, cachedData)

            val eventId = "new-event-id"
            val stockDepletedPayload = StockDepletedEventPayload(productId = productId)
            val envelope = createEnvelope(
                id = eventId,
                type = "loopers.stock.depleted.v1",
                aggregateType = "Stock",
                aggregateId = productId.toString(),
                payload = objectMapper.writeValueAsString(stockDepletedPayload),
            )
            val records = listOf(createConsumerRecord("stock-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then
            val expectedIdempotencyKey = "product-cache:$eventId"
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
                "stock-events",
                0,
                0L,
                "key",
                "not a valid json",
            )
            val records = listOf(malformedRecord)
            val acknowledgment = createMockAcknowledgment()

            // when & then
            assertThrows<BatchListenerFailedException> {
                productStockEventConsumer.consume(records, acknowledgment)
            }
        }
    }

    @DisplayName("이벤트 필터링 테스트")
    @Nested
    inner class EventFiltering {

        @DisplayName("지원하지 않는 이벤트 타입은 조용히 건너뛴다")
        @Test
        fun `silently skips unsupported event types`() {
            // given
            val productId = 100L
            val cacheKey = ProductCacheKeys.ProductDetail(productId)
            val cachedData = createCachedProductData(productId)
            cacheTemplate.put(cacheKey, cachedData)

            val unsupportedEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "100",
                payload = "{}",
            )
            val records = listOf(createConsumerRecord("stock-events", unsupportedEnvelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then
            val cacheAfterConsume = cacheTemplate.get(
                cacheKey,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cacheAfterConsume).isNotNull
        }
    }

    @DisplayName("순차 처리 테스트")
    @Nested
    inner class SequentialProcessing {

        @DisplayName("여러 재고 소진 이벤트를 순차적으로 처리한다")
        @Test
        fun `processes multiple stock depleted events sequentially`() {
            // given
            val productId1 = 100L
            val productId2 = 200L
            val cacheKey1 = ProductCacheKeys.ProductDetail(productId1)
            val cacheKey2 = ProductCacheKeys.ProductDetail(productId2)

            cacheTemplate.put(cacheKey1, createCachedProductData(productId1))
            cacheTemplate.put(cacheKey2, createCachedProductData(productId2))

            // given - verify caches exist before consumption
            assertThat(
                cacheTemplate.get(
                    cacheKey1,
                    object : TypeReference<Map<String, Any>>() {},
                ),
            ).isNotNull
            assertThat(
                cacheTemplate.get(
                    cacheKey2,
                    object : TypeReference<Map<String, Any>>() {},
                ),
            ).isNotNull

            val stockDepletedPayload1 = StockDepletedEventPayload(productId = productId1)
            val envelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.stock.depleted.v1",
                aggregateType = "Stock",
                aggregateId = productId1.toString(),
                payload = objectMapper.writeValueAsString(stockDepletedPayload1),
            )

            val stockDepletedPayload2 = StockDepletedEventPayload(productId = productId2)
            val envelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.stock.depleted.v1",
                aggregateType = "Stock",
                aggregateId = productId2.toString(),
                payload = objectMapper.writeValueAsString(stockDepletedPayload2),
            )

            val records = listOf(
                createConsumerRecord("stock-events", envelope1),
                createConsumerRecord("stock-events", envelope2),
            )
            val acknowledgment = createMockAcknowledgment()

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then - both caches are invalidated
            val cache1AfterConsume = cacheTemplate.get(
                cacheKey1,
                object : TypeReference<Map<String, Any>>() {},
            )
            val cache2AfterConsume = cacheTemplate.get(
                cacheKey2,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cache1AfterConsume).isNull()
            assertThat(cache2AfterConsume).isNull()

            // then - both idempotency keys exist
            assertThat(eventHandledRepository.existsByIdempotencyKey("product-cache:event-1")).isTrue()
            assertThat(eventHandledRepository.existsByIdempotencyKey("product-cache:event-2")).isTrue()
        }
    }

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

    private fun createMockAcknowledgment(): Acknowledgment {
        val ack = mockk<Acknowledgment>()
        every { ack.acknowledge() } just runs
        return ack
    }
}
