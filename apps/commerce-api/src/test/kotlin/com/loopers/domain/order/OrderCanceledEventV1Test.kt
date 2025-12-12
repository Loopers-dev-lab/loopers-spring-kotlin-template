package com.loopers.domain.order

import com.loopers.support.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class OrderCanceledEventV1Test {

    @DisplayName("OrderCanceledEventV1 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("DomainEvent 인터페이스를 구현한다")
        @Test
        fun `event implements DomainEvent`() {
            // given
            val orderId = 1L
            val orderItems = listOf(
                OrderCreatedEventV1.OrderItemSnapshot(productId = 100L, quantity = 2),
            )

            // when
            val event = OrderCanceledEventV1(
                orderId = orderId,
                orderItems = orderItems,
            )

            // then
            assertThat(event).isInstanceOf(DomainEvent::class.java)
        }

        @DisplayName("eventType은 'OrderCanceledEventV1'이다")
        @Test
        fun `eventType is OrderCanceledEventV1`() {
            // given
            val orderId = 1L
            val orderItems = emptyList<OrderCreatedEventV1.OrderItemSnapshot>()

            // when
            val event = OrderCanceledEventV1(
                orderId = orderId,
                orderItems = orderItems,
            )

            // then
            assertThat(event.eventType).isEqualTo("OrderCanceledEventV1")
        }

        @DisplayName("aggregateType은 'Order'이다")
        @Test
        fun `aggregateType is Order`() {
            // given
            val orderId = 1L
            val orderItems = emptyList<OrderCreatedEventV1.OrderItemSnapshot>()

            // when
            val event = OrderCanceledEventV1(
                orderId = orderId,
                orderItems = orderItems,
            )

            // then
            assertThat(event.aggregateType).isEqualTo("Order")
        }

        @DisplayName("aggregateId는 orderId를 문자열로 변환한 값이다")
        @Test
        fun `aggregateId is orderId as string`() {
            // given
            val orderId = 456L
            val orderItems = emptyList<OrderCreatedEventV1.OrderItemSnapshot>()

            // when
            val event = OrderCanceledEventV1(
                orderId = orderId,
                orderItems = orderItems,
            )

            // then
            assertThat(event.aggregateId).isEqualTo("456")
        }

        @DisplayName("version은 1이다")
        @Test
        fun `version is 1`() {
            // given
            val orderId = 1L
            val orderItems = emptyList<OrderCreatedEventV1.OrderItemSnapshot>()

            // when
            val event = OrderCanceledEventV1(
                orderId = orderId,
                orderItems = orderItems,
            )

            // then
            assertThat(event.version).isEqualTo(1)
        }
    }
}
