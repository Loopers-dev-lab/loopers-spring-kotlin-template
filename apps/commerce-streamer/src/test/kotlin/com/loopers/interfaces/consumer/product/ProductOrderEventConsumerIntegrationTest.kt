package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.product.ProductStatistic
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.product.ProductStatisticJpaRepository
import com.loopers.interfaces.consumer.product.event.OrderPaidEventPayload
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
 * ProductOrderEventConsumer 통합 테스트
 *
 * Testcontainers Kafka를 사용하여 실제 메시지 처리를 검증합니다.
 *
 * 검증 범위:
 * - 메시지 수신 → 처리 → DB 상태 변경
 * - 지원하지 않는 이벤트 타입 필터링
 * - 실패 시 데이터 안전성
 *
 */
@SpringBootTest
@DisplayName("ProductOrderEventConsumer 통합 테스트")
class ProductOrderEventConsumerIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val productStatisticJpaRepository: ProductStatisticJpaRepository,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val TOPIC = "order-events"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    @DisplayName("주문 결제 이벤트 수신 시 판매 수량이 증가한다")
    fun `increases sales count when order paid event received`() {
        // given
        val productStatistic = saveProductStatistic(productId = 100L, salesCount = 0)

        val envelope = createOrderPaidEnvelope(
            aggregateId = "order-1",
            orderItems = listOf(
                OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 3),
            ),
        )

        // when
        kafkaTemplate.send(TOPIC, "order-1", objectMapper.writeValueAsString(envelope)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(productStatistic.productId)
            assertThat(result).isNotNull
            assertThat(result!!.salesCount).isEqualTo(3)
        }
    }

    @Test
    @DisplayName("배치로 여러 메시지 수신 시 각 상품의 판매 수량이 올바르게 증가한다")
    fun `increases sales count correctly for batch messages`() {
        // given
        saveProductStatistic(productId = 100L, salesCount = 5)
        saveProductStatistic(productId = 200L, salesCount = 10)

        val envelope1 = createOrderPaidEnvelope(
            aggregateId = "order-1",
            orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 2)),
        )
        val envelope2 = createOrderPaidEnvelope(
            aggregateId = "order-2",
            orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 200L, quantity = 3)),
        )

        // when
        kafkaTemplate.send(TOPIC, "order-1", objectMapper.writeValueAsString(envelope1)).get()
        kafkaTemplate.send(TOPIC, "order-2", objectMapper.writeValueAsString(envelope2)).get()

        // then
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val result1 = productStatisticJpaRepository.findByProductId(100L)
            val result2 = productStatisticJpaRepository.findByProductId(200L)

            assertThat(result1!!.salesCount).isEqualTo(7) // 5 + 2
            assertThat(result2!!.salesCount).isEqualTo(13) // 10 + 3
        }
    }

    @Test
    @DisplayName("지원하지 않는 이벤트 타입은 무시하고 판매 수량이 변경되지 않는다")
    fun `ignores unsupported event types`() {
        // given
        val initialSalesCount = 10L
        saveProductStatistic(productId = 100L, salesCount = initialSalesCount)

        val unsupportedEnvelope = createEnvelope(
            type = "loopers.order.created.v1",
            aggregateId = "order-1",
            payload = "{}",
        )

        // when
        kafkaTemplate.send(TOPIC, "order-1", objectMapper.writeValueAsString(unsupportedEnvelope)).get()

        // then
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(100L)
            assertThat(result!!.salesCount).isEqualTo(initialSalesCount)
        }
    }

    @Test
    @DisplayName("잘못된 JSON 포맷의 메시지는 기존 데이터에 영향을 주지 않는다")
    fun `malformed json does not affect existing data`() {
        // given
        val initialSalesCount = 10L
        saveProductStatistic(productId = 100L, salesCount = initialSalesCount)
        val malformedJson = """{"orderId": "order-1", "broken": """

        // when
        kafkaTemplate.send(TOPIC, "key-1", malformedJson).get()

        // then
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(7)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(100L)
            assertThat(result!!.salesCount).isEqualTo(initialSalesCount)
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

    private fun createOrderPaidEnvelope(
        aggregateId: String,
        orderItems: List<OrderPaidEventPayload.OrderItem>,
    ): CloudEventEnvelope {
        val payload = OrderPaidEventPayload(
            orderId = aggregateId.hashCode().toLong(),
            orderItems = orderItems,
        )
        return createEnvelope(
            type = "loopers.order.paid.v1",
            aggregateType = "Order",
            aggregateId = aggregateId,
            payload = objectMapper.writeValueAsString(payload),
        )
    }

    private fun createEnvelope(
        id: String = UUID.randomUUID().toString(),
        type: String,
        aggregateType: String = "Order",
        aggregateId: String,
        payload: String,
    ): CloudEventEnvelope = CloudEventEnvelope(
        id = id,
        type = type,
        source = "test-source",
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        time = Instant.now(),
        payload = payload,
    )
}
