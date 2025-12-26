package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.product.ProductStatistic
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository
import com.loopers.infrastructure.product.ProductStatisticJpaRepository
import com.loopers.domain.product.event.OrderPaidEvent
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
 * ProductOrderEventConsumer 멱등성 통합 테스트
 *
 * 중복 메시지 처리 시 멱등성 보장을 검증합니다.
 *
 * 검증 범위:
 * - 동일한 메시지 중복 수신 → 한 번만 처리
 * - 이미 처리된 이벤트 → 무시
 * - 멱등성 키 저장 확인
 */
@SpringBootTest
@DisplayName("ProductOrderEventConsumer 멱등성 테스트")
class ProductOrderEventConsumerIdempotencyIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val productStatisticJpaRepository: ProductStatisticJpaRepository,
    private val eventHandledRepository: EventHandledRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
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
    @DisplayName("동일한 메시지가 중복 도착해도 판매 수량은 한 번만 증가한다")
    fun `increases sales count only once when duplicate messages arrive`() {
        // given
        val initialSalesCount = 10L
        val orderQuantity = 5
        saveProductStatistic(productId = 100L, salesCount = initialSalesCount)

        val aggregateId = "order-duplicate-test-${UUID.randomUUID()}"
        val envelope = createOrderPaidEnvelope(
            aggregateId = aggregateId,
            orderItems = listOf(
                OrderPaidEvent.OrderItem(productId = 100L, quantity = orderQuantity),
            ),
        )
        val messageJson = objectMapper.writeValueAsString(envelope)

        // when - 동일한 메시지 3번 전송
        repeat(3) { kafkaTemplate.send(TOPIC, aggregateId, messageJson).get() }

        // then - 처리 완료 대기
        val expectedCount = initialSalesCount + orderQuantity
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(100L)
            assertThat(result!!.salesCount).isEqualTo(expectedCount)
        }

        // then - 추가 시간 동안 값 변경 없음 확인 (멱등성 검증)
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(100L)
            assertThat(result!!.salesCount).isEqualTo(expectedCount)
        }
    }

    @Test
    @DisplayName("이미 처리된 이벤트 수신 시 판매 수량이 변경되지 않는다")
    fun `ignores already processed event`() {
        // given
        val initialSalesCount = 10L
        saveProductStatistic(productId = 100L, salesCount = initialSalesCount)

        val aggregateId = "already-processed-order"
        val idempotencyKey = "product-statistic:Order:$aggregateId:paid"
        eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = idempotencyKey))

        val envelope = createOrderPaidEnvelope(
            aggregateId = aggregateId,
            orderItems = listOf(
                OrderPaidEvent.OrderItem(productId = 100L, quantity = 5),
            ),
        )

        // when
        kafkaTemplate.send(TOPIC, aggregateId, objectMapper.writeValueAsString(envelope)).get()

        // then
        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted {
            val result = productStatisticJpaRepository.findByProductId(100L)
            assertThat(result!!.salesCount).isEqualTo(initialSalesCount)
        }
    }

    @Test
    @DisplayName("이벤트 처리 후 멱등성 키가 DB에 저장된다")
    fun `persists idempotency key after processing`() {
        // given
        saveProductStatistic(productId = 100L, salesCount = 0)

        val aggregateId = "order-for-key-test-${UUID.randomUUID()}"
        val envelope = createOrderPaidEnvelope(
            aggregateId = aggregateId,
            orderItems = listOf(
                OrderPaidEvent.OrderItem(productId = 100L, quantity = 1),
            ),
        )

        // when
        kafkaTemplate.send(TOPIC, aggregateId, objectMapper.writeValueAsString(envelope)).get()

        // then
        val expectedIdempotencyKey = "product-statistic:Order:$aggregateId:paid"
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
        salesCount: Long = 0,
    ): ProductStatistic = productStatisticJpaRepository.saveAndFlush(
        ProductStatistic(
            productId = productId,
            viewCount = 0,
            likeCount = 0,
            salesCount = salesCount,
        ),
    )

    private fun createOrderPaidEnvelope(
        aggregateId: String,
        orderItems: List<OrderPaidEvent.OrderItem>,
    ): CloudEventEnvelope {
        val payload = OrderPaidEvent(
            orderId = aggregateId.hashCode().toLong(),
            orderItems = orderItems,
        )
        return CloudEventEnvelope(
            id = UUID.randomUUID().toString(),
            type = "loopers.order.paid.v1",
            source = "test-source",
            aggregateType = "Order",
            aggregateId = aggregateId,
            time = Instant.now(),
            payload = objectMapper.writeValueAsString(payload),
        )
    }
}
