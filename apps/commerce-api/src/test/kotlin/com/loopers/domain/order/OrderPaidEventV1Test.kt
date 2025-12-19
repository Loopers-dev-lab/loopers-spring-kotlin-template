package com.loopers.domain.order

import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

@DisplayName("OrderPaidEventV1 테스트")
class OrderPaidEventV1Test {

    @DisplayName("from 팩토리 메서드 테스트")
    @Nested
    inner class From {

        @DisplayName("Order에서 올바른 속성으로 이벤트가 생성된다")
        @Test
        fun `from() factory creates event with correct properties`() {
            // given
            val order = Order.of(
                userId = 1L,
                totalAmount = Money.krw(50000),
                status = OrderStatus.PLACED,
                orderItems = mutableListOf(
                    OrderItem.create(
                        productId = 100L,
                        quantity = 2,
                        productName = "상품1",
                        unitPrice = Money.krw(15000),
                    ),
                    OrderItem.create(
                        productId = 200L,
                        quantity = 1,
                        productName = "상품2",
                        unitPrice = Money.krw(20000),
                    ),
                ),
            )

            // when
            val event = OrderPaidEventV1.from(order)

            // then
            assertThat(event.orderId).isEqualTo(order.id)
            assertThat(event.userId).isEqualTo(1L)
            assertThat(event.totalAmount).isEqualTo(50000L)
            assertThat(event.orderItems).hasSize(2)
            assertThat(event.orderItems[0].productId).isEqualTo(100L)
            assertThat(event.orderItems[0].quantity).isEqualTo(2)
            assertThat(event.orderItems[1].productId).isEqualTo(200L)
            assertThat(event.orderItems[1].quantity).isEqualTo(1)
            assertThat(event.occurredAt).isNotNull()
        }

        @DisplayName("빈 orderItems로 이벤트가 생성된다")
        @Test
        fun `from() factory creates event with empty orderItems`() {
            // given
            val order = Order.of(
                userId = 2L,
                totalAmount = Money.krw(0),
                status = OrderStatus.PLACED,
                orderItems = mutableListOf(),
            )

            // when
            val event = OrderPaidEventV1.from(order)

            // then
            assertThat(event.orderId).isEqualTo(order.id)
            assertThat(event.userId).isEqualTo(2L)
            assertThat(event.totalAmount).isEqualTo(0L)
            assertThat(event.orderItems).isEmpty()
        }
    }

    @DisplayName("OrderItemSnapshot 테스트")
    @Nested
    inner class OrderItemSnapshotTest {

        @DisplayName("OrderItemSnapshot이 올바르게 생성된다")
        @Test
        fun `OrderItemSnapshot is created with correct values`() {
            // given & when
            val snapshot = OrderPaidEventV1.OrderItemSnapshot(
                productId = 100L,
                quantity = 5,
            )

            // then
            assertThat(snapshot.productId).isEqualTo(100L)
            assertThat(snapshot.quantity).isEqualTo(5)
        }
    }
}
