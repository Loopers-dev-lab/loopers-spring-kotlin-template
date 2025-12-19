package com.loopers.infrastructure.outbox

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
import com.loopers.interfaces.event.order.OrderEventListener
import com.loopers.interfaces.event.payment.PaymentEventListener
import com.loopers.interfaces.event.product.ProductEventListener
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate

/**
 * OutboxEventListener 통합 테스트
 *
 * 검증 범위:
 * - 각 DomainEvent 발행 시 Outbox 테이블에 저장되는지 확인
 * - aggregateType, aggregateId가 올바르게 추출되는지 확인
 * - BEFORE_COMMIT 시점에 동기적으로 저장되는지 확인
 */
@SpringBootTest
@DisplayName("OutboxEventListener 통합 테스트")
class OutboxEventListenerIntegrationTest @Autowired constructor(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val outboxRepository: OutboxRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
) {
    // Mock other event listeners to isolate OutboxEventListener tests
    @MockkBean(relaxed = true)
    private lateinit var productEventListener: ProductEventListener

    @MockkBean(relaxed = true)
    private lateinit var orderEventListener: OrderEventListener

    @MockkBean(relaxed = true)
    private lateinit var paymentEventListener: PaymentEventListener

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("Order Events")
    inner class OrderEvents {

        @Test
        @DisplayName("OrderCreatedEventV1 발행 시 Outbox에 aggregateType=Order, aggregateId=orderId로 저장된다")
        fun `OrderCreatedEventV1 triggers Outbox save with aggregateType Order and aggregateId orderId`() {
            // given
            val orderId = 123L
            val event = OrderCreatedEventV1(
                orderId = orderId,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 1L, quantity = 2),
                ),
            )

            // when - BEFORE_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Order")
            assertThat(outboxes[0].aggregateId).isEqualTo(orderId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.order.created.v1")
        }

        @Test
        @DisplayName("OrderCanceledEventV1 발행 시 Outbox에 aggregateType=Order, aggregateId=orderId로 저장된다")
        fun `OrderCanceledEventV1 triggers Outbox save with aggregateType Order and aggregateId orderId`() {
            // given
            val orderId = 456L
            val event = OrderCanceledEventV1(
                orderId = orderId,
                orderItems = listOf(
                    OrderCanceledEventV1.OrderItemSnapshot(productId = 1L, quantity = 2),
                ),
            )

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Order")
            assertThat(outboxes[0].aggregateId).isEqualTo(orderId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.order.canceled.v1")
        }

        @Test
        @DisplayName("OrderPaidEventV1 발행 시 Outbox에 aggregateType=Order, aggregateId=orderId로 저장된다")
        fun `OrderPaidEventV1 triggers Outbox save with aggregateType Order and aggregateId orderId`() {
            // given
            val orderId = 789L
            val event = OrderPaidEventV1(
                orderId = orderId,
                userId = 1L,
                totalAmount = 10000L,
                orderItems = listOf(
                    OrderPaidEventV1.OrderItemSnapshot(productId = 1L, quantity = 2),
                ),
            )

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Order")
            assertThat(outboxes[0].aggregateId).isEqualTo(orderId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.order.paid.v1")
        }
    }

    @Nested
    @DisplayName("Payment Events")
    inner class PaymentEvents {

        @Test
        @DisplayName("PaymentCreatedEventV1 발행 시 Outbox에 aggregateType=Payment, aggregateId=paymentId로 저장된다")
        fun `PaymentCreatedEventV1 triggers Outbox save with aggregateType Payment and aggregateId paymentId`() {
            // given
            val paymentId = 789L
            val event = PaymentCreatedEventV1(paymentId = paymentId)

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Payment")
            assertThat(outboxes[0].aggregateId).isEqualTo(paymentId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.payment.created.v1")
        }

        @Test
        @DisplayName("PaymentPaidEventV1 발행 시 Outbox에 aggregateType=Payment, aggregateId=paymentId로 저장된다")
        fun `PaymentPaidEventV1 triggers Outbox save with aggregateType Payment and aggregateId paymentId`() {
            // given
            val paymentId = 101L
            val event = PaymentPaidEventV1(paymentId = paymentId, orderId = 1L)

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Payment")
            assertThat(outboxes[0].aggregateId).isEqualTo(paymentId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.payment.paid.v1")
        }

        @Test
        @DisplayName("PaymentFailedEventV1 발행 시 Outbox에 aggregateType=Payment, aggregateId=paymentId로 저장된다")
        fun `PaymentFailedEventV1 triggers Outbox save with aggregateType Payment and aggregateId paymentId`() {
            // given
            val paymentId = 202L
            val event = PaymentFailedEventV1(
                paymentId = paymentId,
                orderId = 1L,
                userId = 1L,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
            )

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Payment")
            assertThat(outboxes[0].aggregateId).isEqualTo(paymentId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.payment.failed.v1")
        }
    }

    @Nested
    @DisplayName("Like Events")
    inner class LikeEvents {

        @Test
        @DisplayName("LikeCreatedEventV1 발행 시 Outbox에 aggregateType=Like, aggregateId=productId로 저장된다")
        fun `LikeCreatedEventV1 triggers Outbox save with aggregateType Like and aggregateId productId`() {
            // given
            val productId = 303L
            val event = LikeCreatedEventV1(userId = 1L, productId = productId)

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Like")
            assertThat(outboxes[0].aggregateId).isEqualTo(productId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.like.created.v1")
        }

        @Test
        @DisplayName("LikeCanceledEventV1 발행 시 Outbox에 aggregateType=Like, aggregateId=productId로 저장된다")
        fun `LikeCanceledEventV1 triggers Outbox save with aggregateType Like and aggregateId productId`() {
            // given
            val productId = 404L
            val event = LikeCanceledEventV1(userId = 1L, productId = productId)

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Like")
            assertThat(outboxes[0].aggregateId).isEqualTo(productId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.like.canceled.v1")
        }
    }

    @Nested
    @DisplayName("Product Events")
    inner class ProductEvents {

        @Test
        @DisplayName("ProductViewedEventV1 발행 시 Outbox에 aggregateType=Product, aggregateId=productId로 저장된다")
        fun `ProductViewedEventV1 triggers Outbox save with aggregateType Product and aggregateId productId`() {
            // given
            val productId = 505L
            val event = ProductViewedEventV1.create(productId = productId, userId = 1L)

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Product")
            assertThat(outboxes[0].aggregateId).isEqualTo(productId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.product.viewed.v1")
        }

        @Test
        @DisplayName("ProductViewedEventV1 발행 시 userId가 null이어도 Outbox에 저장된다")
        fun `ProductViewedEventV1 triggers Outbox save even when userId is null`() {
            // given
            val productId = 606L
            val event = ProductViewedEventV1.create(productId = productId, userId = null)

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Product")
            assertThat(outboxes[0].aggregateId).isEqualTo(productId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.product.viewed.v1")
        }
    }

    @Nested
    @DisplayName("Stock Events")
    inner class StockEvents {

        @Test
        @DisplayName("StockDepletedEventV1 발행 시 Outbox에 aggregateType=Stock, aggregateId=productId로 저장된다")
        fun `StockDepletedEventV1 triggers Outbox save with aggregateType Stock and aggregateId productId`() {
            // given
            val productId = 707L
            val stockId = 1L
            val event = StockDepletedEventV1(productId = productId, stockId = stockId)

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).hasSize(1)
            assertThat(outboxes[0].aggregateType).isEqualTo("Stock")
            assertThat(outboxes[0].aggregateId).isEqualTo(productId.toString())
            assertThat(outboxes[0].eventType).isEqualTo("loopers.stock.depleted.v1")
        }
    }
}
