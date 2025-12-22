package com.loopers.interfaces.consumer.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.product.ProductStatistic
import com.loopers.eventschema.CloudEventEnvelope
import com.loopers.infrastructure.idempotency.EventHandledJpaRepository
import com.loopers.infrastructure.product.ProductStatisticJpaRepository
import com.loopers.interfaces.consumer.product.event.OrderPaidEventPayload
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
@DisplayName("ProductOrderEventConsumer 통합 테스트")
class ProductOrderEventConsumerIntegrationTest @Autowired constructor(
    private val productOrderEventConsumer: ProductOrderEventConsumer,
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

    @DisplayName("판매 수량 증가 테스트")
    @Nested
    inner class SalesCountIncrement {

        @DisplayName("주문 결제 이벤트 소비 시 판매 수량이 증가한다")
        @Test
        fun `increases sales count when order paid event is consumed`() {
            // given
            val productStatistic1 = createProductStatistic(productId = 100L, salesCount = 0)
            val productStatistic2 = createProductStatistic(productId = 200L, salesCount = 0)
            productStatisticJpaRepository.saveAllAndFlush(listOf(productStatistic1, productStatistic2))

            val orderPaidPayload = createOrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(
                    OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 2),
                    OrderPaidEventPayload.OrderItem(productId = 200L, quantity = 1),
                ),
            )
            val envelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(orderPaidPayload),
            )
            val records = listOf(createConsumerRecord("order-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            val result1 = productStatisticJpaRepository.findByProductId(100L)
            val result2 = productStatisticJpaRepository.findByProductId(200L)

            assertThat(result1).isNotNull
            assertThat(result1!!.salesCount).isEqualTo(2)
            assertThat(result2).isNotNull
            assertThat(result2!!.salesCount).isEqualTo(1)
        }

        @DisplayName("배치로 여러 주문 이벤트 소비 시 각 상품의 판매 수량이 올바르게 증가한다")
        @Test
        fun `increases sales count correctly for multiple records`() {
            // given
            val productStatistic1 = createProductStatistic(productId = 100L, salesCount = 5)
            val productStatistic2 = createProductStatistic(productId = 200L, salesCount = 10)
            productStatisticJpaRepository.saveAllAndFlush(listOf(productStatistic1, productStatistic2))

            val orderPayload1 = createOrderPaidEventPayload(
                orderId = 1L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 100L, quantity = 1)),
            )
            val envelope1 = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "1",
                payload = objectMapper.writeValueAsString(orderPayload1),
            )

            val orderPayload2 = createOrderPaidEventPayload(
                orderId = 2L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = 200L, quantity = 3)),
            )
            val envelope2 = createEnvelope(
                id = "event-2",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = "2",
                payload = objectMapper.writeValueAsString(orderPayload2),
            )

            val records = listOf(
                createConsumerRecord("order-events", envelope1),
                createConsumerRecord("order-events", envelope2),
            )
            val acknowledgment = createMockAcknowledgment()

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            val result1 = productStatisticJpaRepository.findByProductId(100L)
            val result2 = productStatisticJpaRepository.findByProductId(200L)

            assertThat(result1).isNotNull
            assertThat(result1!!.salesCount).isEqualTo(6)
            assertThat(result2).isNotNull
            assertThat(result2!!.salesCount).isEqualTo(13)
        }

        @DisplayName("빈 배치 소비 시 판매 수량이 변경되지 않는다")
        @Test
        fun `sales count unchanged for empty batch`() {
            // given
            val productId = 100L
            val initialSalesCount = 10L
            val productStatistic = createProductStatistic(productId = productId, salesCount = initialSalesCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val records = emptyList<ConsumerRecord<String, String>>()
            val acknowledgment = createMockAcknowledgment()

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.salesCount).isEqualTo(initialSalesCount)
        }
    }

    @DisplayName("멱등성 테스트")
    @Nested
    inner class IdempotencyCheck {

        @DisplayName("이미 처리된 이벤트 소비 시 판매 수량이 변경되지 않는다")
        @Test
        fun `sales count unchanged when event already processed`() {
            // given
            val productId = 100L
            val initialSalesCount = 10L
            val productStatistic = createProductStatistic(productId = productId, salesCount = initialSalesCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val aggregateId = "123"
            val idempotencyKey = "product-statistic:Order:$aggregateId:paid"
            eventHandledJpaRepository.saveAndFlush(EventHandled(idempotencyKey = idempotencyKey))

            val orderPaidPayload = createOrderPaidEventPayload(
                orderId = 123L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = productId, quantity = 5)),
            )
            val envelope = createEnvelope(
                id = "event-1",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = aggregateId,
                payload = objectMapper.writeValueAsString(orderPaidPayload),
            )
            val records = listOf(createConsumerRecord("order-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.salesCount).isEqualTo(initialSalesCount)
        }

        @DisplayName("이벤트 처리 후 멱등성 키가 DB에 저장된다")
        @Test
        fun `idempotency key exists in DB after processing`() {
            // given
            val productId = 100L
            val productStatistic = createProductStatistic(productId = productId, salesCount = 0L)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val aggregateId = "456"
            val orderPaidPayload = createOrderPaidEventPayload(
                orderId = 456L,
                orderItems = listOf(OrderPaidEventPayload.OrderItem(productId = productId, quantity = 1)),
            )
            val envelope = createEnvelope(
                id = "event-new",
                type = "loopers.order.paid.v1",
                aggregateType = "Order",
                aggregateId = aggregateId,
                payload = objectMapper.writeValueAsString(orderPaidPayload),
            )
            val records = listOf(createConsumerRecord("order-events", envelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            val expectedIdempotencyKey = "product-statistic:Order:$aggregateId:paid"
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
                "order-events",
                0,
                0L,
                "key",
                "not a valid json",
            )
            val records = listOf(malformedRecord)
            val acknowledgment = createMockAcknowledgment()

            // when & then
            assertThrows<BatchListenerFailedException> {
                productOrderEventConsumer.consume(records, acknowledgment)
            }
        }
    }

    @DisplayName("지원하지 않는 이벤트 필터링")
    @Nested
    inner class UnsupportedEventFilter {

        @DisplayName("지원하지 않는 이벤트 타입 소비 시 판매 수량이 변경되지 않는다")
        @Test
        fun `silently skips unsupported event types`() {
            // given
            val productId = 100L
            val initialSalesCount = 10L
            val productStatistic = createProductStatistic(productId = productId, salesCount = initialSalesCount)
            productStatisticJpaRepository.saveAndFlush(productStatistic)

            val unsupportedEnvelope = createEnvelope(
                id = "event-1",
                type = "loopers.like.created.v1",
                aggregateType = "Like",
                aggregateId = "100",
                payload = "{}",
            )
            val records = listOf(createConsumerRecord("order-events", unsupportedEnvelope))
            val acknowledgment = createMockAcknowledgment()

            // when
            productOrderEventConsumer.consume(records, acknowledgment)

            // then
            val result = productStatisticJpaRepository.findByProductId(productId)
            assertThat(result).isNotNull
            assertThat(result!!.salesCount).isEqualTo(initialSalesCount)
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

    private fun createOrderPaidEventPayload(
        orderId: Long,
        orderItems: List<OrderPaidEventPayload.OrderItem>,
    ): OrderPaidEventPayload = OrderPaidEventPayload(
        orderId = orderId,
        orderItems = orderItems,
    )

    private fun createMockAcknowledgment(): Acknowledgment {
        val ack = mockk<Acknowledgment>()
        every { ack.acknowledge() } just runs
        return ack
    }
}
