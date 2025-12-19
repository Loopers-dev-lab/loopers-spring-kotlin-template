package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.loopers.application.product.ProductCacheKeys
import com.loopers.cache.CacheTemplate
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.interfaces.consumer.product.event.StockDepletedEventPayload
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

@DisplayName("ProductStockEventConsumer 배치 처리 단위 테스트")
class ProductStockEventConsumerTest {

    private lateinit var cacheTemplate: CacheTemplate
    private lateinit var productEventMapper: ProductEventMapper
    private lateinit var objectMapper: ObjectMapper
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var productStockEventConsumer: ProductStockEventConsumer

    @BeforeEach
    fun setUp() {
        cacheTemplate = mockk()
        objectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        productEventMapper = ProductEventMapper(objectMapper)
        acknowledgment = mockk()

        productStockEventConsumer = ProductStockEventConsumer(
            cacheTemplate,
            productEventMapper,
            objectMapper,
        )
    }

    @DisplayName("Stock Depleted 이벤트 처리")
    @Nested
    inner class HandleStockDepletedEvents {

        @DisplayName("Stock Depleted 이벤트 배치로 해당 상품 캐시를 무효화한다")
        @Test
        fun `evicts product cache for stock depleted events`() {
            // given
            val stockDepletedPayload1 = StockDepletedEventPayload(productId = 1L)
            val stockDepletedEnvelope1 = createEnvelope(
                type = "loopers.stock.depleted.v1",
                aggregateType = "Stock",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(stockDepletedPayload1),
            )

            val stockDepletedPayload2 = StockDepletedEventPayload(productId = 2L)
            val stockDepletedEnvelope2 = createEnvelope(
                type = "loopers.stock.depleted.v1",
                aggregateType = "Stock",
                aggregateId = "2",
                payload = objectMapper.writeValueAsString(stockDepletedPayload2),
            )

            val records = listOf(
                createConsumerRecord("stock-events", stockDepletedEnvelope1),
                createConsumerRecord("stock-events", stockDepletedEnvelope2),
            )

            every { cacheTemplate.evictAll(any<List<ProductCacheKeys.ProductDetail>>()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                cacheTemplate.evictAll(
                    match<List<ProductCacheKeys.ProductDetail>> { cacheKeys ->
                        cacheKeys.size == 2 &&
                            cacheKeys[0].key == "product-detail:v1:1" &&
                            cacheKeys[1].key == "product-detail:v1:2"
                    },
                )
            }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("빈 배치인 경우 evictAll()을 호출하지 않는다")
        @Test
        fun `does not call evictAll for empty batch`() {
            // given
            val records = emptyList<ConsumerRecord<String, String>>()

            every { acknowledgment.acknowledge() } just runs

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { cacheTemplate.evictAll(any<List<ProductCacheKeys.ProductDetail>>()) }
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
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "100",
                payload = "{}",
            )

            val records = listOf(createConsumerRecord("stock-events", unsupportedEnvelope))

            every { acknowledgment.acknowledge() } just runs

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 0) { cacheTemplate.evictAll(any<List<ProductCacheKeys.ProductDetail>>()) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @DisplayName("지원하는 이벤트와 지원하지 않는 이벤트가 섞인 경우 지원하는 이벤트만 처리한다")
        @Test
        fun `processes only supported events when mixed with unsupported`() {
            // given
            val stockPayload = StockDepletedEventPayload(productId = 1L)
            val stockEnvelope = createEnvelope(
                type = "loopers.stock.depleted.v1",
                aggregateType = "Stock",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(stockPayload),
            )

            val unsupportedEnvelope = createEnvelope(
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "100",
                payload = "{}",
            )

            val records = listOf(
                createConsumerRecord("stock-events", stockEnvelope),
                createConsumerRecord("stock-events", unsupportedEnvelope),
            )

            every { cacheTemplate.evictAll(any<List<ProductCacheKeys.ProductDetail>>()) } just runs
            every { acknowledgment.acknowledge() } just runs

            // when
            productStockEventConsumer.consume(records, acknowledgment)

            // then
            verify(exactly = 1) {
                cacheTemplate.evictAll(
                    match<List<ProductCacheKeys.ProductDetail>> { cacheKeys ->
                        cacheKeys.size == 1 &&
                            cacheKeys[0].key == "product-detail:v1:1"
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
                "stock-events",
                0,
                0L,
                "key",
                "not a valid json",
            )

            val records = listOf(malformedRecord)

            // when & then
            assertThrows<BatchListenerFailedException> {
                productStockEventConsumer.consume(records, acknowledgment)
            }
        }

        @DisplayName("첫 번째 메시지가 파싱 실패하면 나머지 메시지는 처리되지 않는다")
        @Test
        fun `does not process remaining messages when first message fails`() {
            // given
            val malformedRecord = ConsumerRecord(
                "stock-events",
                0,
                0L,
                "key",
                "not a valid json",
            )

            val stockPayload = StockDepletedEventPayload(productId = 1L)
            val validEnvelope = createEnvelope(
                type = "loopers.stock.depleted.v1",
                aggregateType = "Stock",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(stockPayload),
            )
            val validRecord = createConsumerRecord("stock-events", validEnvelope)

            val records = listOf(malformedRecord, validRecord)

            // when & then
            assertThrows<BatchListenerFailedException> {
                productStockEventConsumer.consume(records, acknowledgment)
            }
            verify(exactly = 0) { cacheTemplate.evictAll(any<List<ProductCacheKeys.ProductDetail>>()) }
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
