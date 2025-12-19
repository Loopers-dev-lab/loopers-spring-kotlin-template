package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.order.OrderCanceledEventV1
import com.loopers.domain.order.OrderCreatedEventV1
import com.loopers.domain.order.OrderPaidEventV1
import com.loopers.domain.payment.PaymentCreatedEventV1
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.payment.PaymentPaidEventV1
import com.loopers.domain.product.ProductViewedEventV1
import com.loopers.domain.product.StockDepletedEventV1
import com.loopers.support.event.DomainEvent
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
@DisplayName("CloudEventEnvelopeFactory 단위 테스트")
class CloudEventEnvelopeFactoryTest @Autowired constructor(
    private val cloudEventEnvelopeFactory: CloudEventEnvelopeFactory,
    private val objectMapper: ObjectMapper,
) {

    @DisplayName("create 메서드")
    @Nested
    inner class Create {

        @DisplayName("UUID 형식의 id를 생성한다")
        @Test
        fun `creates envelope with UUID id`() {
            // given
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
            )

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.id).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        }

        @DisplayName("source를 'commerce-api'로 설정한다")
        @Test
        fun `sets source as commerce-api`() {
            // given
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
            )

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.source).isEqualTo("commerce-api")
        }

        @DisplayName("event.occurredAt으로 time을 설정한다")
        @Test
        fun `sets time from event occurredAt`() {
            // given
            val occurredAt = Instant.parse("2024-01-01T12:00:00Z")
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
                occurredAt = occurredAt,
            )

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.time).isEqualTo(occurredAt)
        }

        @DisplayName("이벤트를 JSON으로 직렬화하여 payload에 저장한다")
        @Test
        fun `serializes event to JSON payload`() {
            // given
            val occurredAt = Instant.parse("2024-01-01T12:00:00Z")
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
                occurredAt = occurredAt,
            )

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            val deserializedEvent = objectMapper.readValue<OrderCreatedEventV1>(envelope!!.payload)
            assertThat(deserializedEvent.orderId).isEqualTo(1L)
            assertThat(deserializedEvent.orderItems).hasSize(1)
            assertThat(deserializedEvent.orderItems[0].productId).isEqualTo(100L)
            assertThat(deserializedEvent.orderItems[0].quantity).isEqualTo(2)
            assertThat(deserializedEvent.occurredAt).isEqualTo(occurredAt)
        }

        @DisplayName("알 수 없는 이벤트 타입에 대해 null을 반환한다")
        @Test
        fun `returns null for unknown event type`() {
            // given
            val unknownEvent = object : DomainEvent {
                override val occurredAt: Instant = Instant.now()
            }

            // when
            val envelope = cloudEventEnvelopeFactory.create(unknownEvent)

            // then
            assertThat(envelope).isNull()
        }
    }

    @DisplayName("이벤트 메타데이터 해석")
    @Nested
    inner class ResolveMetadata {

        @DisplayName("OrderCreatedEventV1 - type: loopers.order.created.v1, aggregateType: Order, aggregateId: orderId")
        @Test
        fun `resolves OrderCreatedEventV1 metadata`() {
            // given
            val event = OrderCreatedEventV1(
                orderId = 123L,
                orderItems = listOf(OrderCreatedEventV1.OrderItemSnapshot(productId = 1L, quantity = 1)),
            )

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.order.created.v1")
            assertThat(envelope.aggregateType).isEqualTo("Order")
            assertThat(envelope.aggregateId).isEqualTo("123")
        }

        @DisplayName("OrderCanceledEventV1 - type: loopers.order.canceled.v1, aggregateType: Order, aggregateId: orderId")
        @Test
        fun `resolves OrderCanceledEventV1 metadata`() {
            // given
            val event = OrderCanceledEventV1(
                orderId = 456L,
                orderItems = listOf(OrderCanceledEventV1.OrderItemSnapshot(productId = 1L, quantity = 1)),
            )

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.order.canceled.v1")
            assertThat(envelope.aggregateType).isEqualTo("Order")
            assertThat(envelope.aggregateId).isEqualTo("456")
        }

        @DisplayName("OrderPaidEventV1 - type: loopers.order.paid.v1, aggregateType: Order, aggregateId: orderId")
        @Test
        fun `resolves OrderPaidEventV1 metadata`() {
            // given
            val event = OrderPaidEventV1(
                orderId = 789L,
                userId = 1L,
                totalAmount = 10000L,
                orderItems = listOf(OrderPaidEventV1.OrderItemSnapshot(productId = 1L, quantity = 1)),
            )

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.order.paid.v1")
            assertThat(envelope.aggregateType).isEqualTo("Order")
            assertThat(envelope.aggregateId).isEqualTo("789")
        }

        @DisplayName("PaymentCreatedEventV1 - type: loopers.payment.created.v1, aggregateType: Payment, aggregateId: paymentId")
        @Test
        fun `resolves PaymentCreatedEventV1 metadata`() {
            // given
            val event = PaymentCreatedEventV1(paymentId = 100L)

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.payment.created.v1")
            assertThat(envelope.aggregateType).isEqualTo("Payment")
            assertThat(envelope.aggregateId).isEqualTo("100")
        }

        @DisplayName("PaymentPaidEventV1 - type: loopers.payment.paid.v1, aggregateType: Payment, aggregateId: paymentId")
        @Test
        fun `resolves PaymentPaidEventV1 metadata`() {
            // given
            val event = PaymentPaidEventV1(paymentId = 200L, orderId = 1L)

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.payment.paid.v1")
            assertThat(envelope.aggregateType).isEqualTo("Payment")
            assertThat(envelope.aggregateId).isEqualTo("200")
        }

        @DisplayName("PaymentFailedEventV1 - type: loopers.payment.failed.v1, aggregateType: Payment, aggregateId: paymentId")
        @Test
        fun `resolves PaymentFailedEventV1 metadata`() {
            // given
            val event = PaymentFailedEventV1(
                paymentId = 300L,
                orderId = 1L,
                userId = 1L,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
            )

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.payment.failed.v1")
            assertThat(envelope.aggregateType).isEqualTo("Payment")
            assertThat(envelope.aggregateId).isEqualTo("300")
        }

        @DisplayName("LikeCreatedEventV1 - type: loopers.like.created.v1, aggregateType: Like, aggregateId: productId")
        @Test
        fun `resolves LikeCreatedEventV1 metadata`() {
            // given
            val event = LikeCreatedEventV1(userId = 1L, productId = 500L)

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.like.created.v1")
            assertThat(envelope.aggregateType).isEqualTo("Like")
            assertThat(envelope.aggregateId).isEqualTo("500")
        }

        @DisplayName("LikeCanceledEventV1 - type: loopers.like.canceled.v1, aggregateType: Like, aggregateId: productId")
        @Test
        fun `resolves LikeCanceledEventV1 metadata`() {
            // given
            val event = LikeCanceledEventV1(userId = 1L, productId = 600L)

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.like.canceled.v1")
            assertThat(envelope.aggregateType).isEqualTo("Like")
            assertThat(envelope.aggregateId).isEqualTo("600")
        }

        @DisplayName("ProductViewedEventV1 - type: loopers.product.viewed.v1, aggregateType: Product, aggregateId: productId")
        @Test
        fun `resolves ProductViewedEventV1 metadata`() {
            // given
            val event = ProductViewedEventV1(productId = 700L, userId = 1L)

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.product.viewed.v1")
            assertThat(envelope.aggregateType).isEqualTo("Product")
            assertThat(envelope.aggregateId).isEqualTo("700")
        }

        @DisplayName("StockDepletedEventV1 - type: loopers.stock.depleted.v1, aggregateType: Stock, aggregateId: productId")
        @Test
        fun `resolves StockDepletedEventV1 metadata`() {
            // given
            val event = StockDepletedEventV1(productId = 800L, stockId = 1L)

            // when
            val envelope = cloudEventEnvelopeFactory.create(event)

            // then
            assertThat(envelope).isNotNull()
            assertThat(envelope!!.type).isEqualTo("loopers.stock.depleted.v1")
            assertThat(envelope.aggregateType).isEqualTo("Stock")
            assertThat(envelope.aggregateId).isEqualTo("800")
        }
    }
}
