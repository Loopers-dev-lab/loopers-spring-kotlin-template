package com.loopers.infrastructure.outbox

import com.loopers.domain.like.LikeCanceledEventV1
import com.loopers.domain.like.LikeCreatedEventV1
import com.loopers.domain.order.OrderPaidEventV1
import com.loopers.domain.product.ProductViewedEventV1
import com.loopers.domain.product.StockDepletedEventV1
import com.loopers.interfaces.event.order.OrderEventListener
import com.loopers.interfaces.event.payment.PaymentEventListener
import com.loopers.interfaces.event.product.ProductEventListener
import com.loopers.support.event.DomainEvent
import com.loopers.support.outbox.OutboxRepository
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
import java.time.Instant

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

    @Nested
    @DisplayName("Unknown Events")
    inner class UnknownEvents {

        @Test
        @DisplayName("알 수 없는 이벤트 타입 발행 시 Outbox에 저장되지 않는다")
        fun `Unknown event type does not trigger Outbox save`() {
            // given
            val unknownEvent = object : DomainEvent {
                override val occurredAt: Instant = Instant.now()
            }

            // when
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(unknownEvent)
            }

            // then
            val outboxes = outboxRepository.findAllByIdGreaterThanOrderByIdAsc(0L, 100)
            assertThat(outboxes).isEmpty()
        }
    }
}
