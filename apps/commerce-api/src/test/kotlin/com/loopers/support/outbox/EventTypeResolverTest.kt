package com.loopers.support.outbox

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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

@DisplayName("EventTypeResolver 단위 테스트")
class EventTypeResolverTest {

    @DisplayName("Order 도메인 이벤트 타입 변환")
    @Nested
    inner class OrderEvents {

        @DisplayName("OrderCreatedEventV1은 'loopers.order.created.v1'을 반환한다")
        @Test
        fun `OrderCreatedEventV1 returns loopers_order_created_v1`() {
            // given
            val event = OrderCreatedEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
            )

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.order.created.v1")
        }

        @DisplayName("OrderCanceledEventV1은 'loopers.order.canceled.v1'을 반환한다")
        @Test
        fun `OrderCanceledEventV1 returns loopers_order_canceled_v1`() {
            // given
            val event = OrderCanceledEventV1(
                orderId = 1L,
                orderItems = listOf(
                    OrderCanceledEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
            )

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.order.canceled.v1")
        }

        @DisplayName("OrderPaidEventV1은 'loopers.order.paid.v1'을 반환한다")
        @Test
        fun `OrderPaidEventV1 returns loopers_order_paid_v1`() {
            // given
            val event = OrderPaidEventV1(
                orderId = 1L,
                userId = 10L,
                totalAmount = 10000L,
                orderItems = listOf(
                    OrderPaidEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
                ),
            )

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.order.paid.v1")
        }
    }

    @DisplayName("Payment 도메인 이벤트 타입 변환")
    @Nested
    inner class PaymentEvents {

        @DisplayName("PaymentCreatedEventV1은 'loopers.payment.created.v1'을 반환한다")
        @Test
        fun `PaymentCreatedEventV1 returns loopers_payment_created_v1`() {
            // given
            val event = PaymentCreatedEventV1(paymentId = 1L)

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.payment.created.v1")
        }

        @DisplayName("PaymentPaidEventV1은 'loopers.payment.paid.v1'을 반환한다")
        @Test
        fun `PaymentPaidEventV1 returns loopers_payment_paid_v1`() {
            // given
            val event = PaymentPaidEventV1(paymentId = 1L, orderId = 100L)

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.payment.paid.v1")
        }

        @DisplayName("PaymentFailedEventV1은 'loopers.payment.failed.v1'을 반환한다")
        @Test
        fun `PaymentFailedEventV1 returns loopers_payment_failed_v1`() {
            // given
            val event = PaymentFailedEventV1(
                paymentId = 1L,
                orderId = 100L,
                userId = 10L,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
            )

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.payment.failed.v1")
        }
    }

    @DisplayName("Like 도메인 이벤트 타입 변환")
    @Nested
    inner class LikeEvents {

        @DisplayName("LikeCreatedEventV1은 'loopers.like.created.v1'을 반환한다")
        @Test
        fun `LikeCreatedEventV1 returns loopers_like_created_v1`() {
            // given
            val event = LikeCreatedEventV1(userId = 1L, productId = 100L)

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.like.created.v1")
        }

        @DisplayName("LikeCanceledEventV1은 'loopers.like.canceled.v1'을 반환한다")
        @Test
        fun `LikeCanceledEventV1 returns loopers_like_canceled_v1`() {
            // given
            val event = LikeCanceledEventV1(userId = 1L, productId = 100L)

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.like.canceled.v1")
        }
    }

    @DisplayName("Product 도메인 이벤트 타입 변환")
    @Nested
    inner class ProductEvents {

        @DisplayName("ProductViewedEventV1은 'loopers.product.viewed.v1'을 반환한다")
        @Test
        fun `ProductViewedEventV1 returns loopers_product_viewed_v1`() {
            // given
            val event = ProductViewedEventV1(productId = 100L, userId = 1L)

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.product.viewed.v1")
        }

        @DisplayName("ProductViewedEventV1은 userId가 null이어도 'loopers.product.viewed.v1'을 반환한다")
        @Test
        fun `ProductViewedEventV1 with null userId returns loopers_product_viewed_v1`() {
            // given
            val event = ProductViewedEventV1(productId = 100L, userId = null)

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.product.viewed.v1")
        }

        @DisplayName("StockDepletedEventV1은 'loopers.stock.depleted.v1'을 반환한다")
        @Test
        fun `StockDepletedEventV1 returns loopers_stock_depleted_v1`() {
            // given
            val event = StockDepletedEventV1(productId = 100L, stockId = 1L)

            // when
            val eventType = EventTypeResolver.resolve(event)

            // then
            assertThat(eventType).isEqualTo("loopers.stock.depleted.v1")
        }
    }

    @DisplayName("예외 케이스")
    @Nested
    inner class ExceptionCases {

        @DisplayName("알 수 없는 이벤트 타입은 IllegalArgumentException을 던진다")
        @Test
        fun `Unknown event type throws IllegalArgumentException`() {
            // given
            val unknownEvent = object : DomainEvent {
                override val occurredAt: Instant = Instant.now()
            }

            // when & then
            assertThatThrownBy { EventTypeResolver.resolve(unknownEvent) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Unknown event type")
        }
    }
}
