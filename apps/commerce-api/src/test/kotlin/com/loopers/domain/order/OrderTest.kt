package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class OrderTest {

    @DisplayName("of 팩토리 생성 테스트")
    @Nested
    inner class Of {

        @DisplayName("유효한 값으로 주문이 생성된다")
        @Test
        fun `create order with valid values`() {
            // given
            val userId = 1L
            val totalAmount = Money.krw(50000)
            val status = OrderStatus.PAID
            val orderItems = mutableListOf(
                createOrderItem(unitPrice = Money.krw(30000)),
                createOrderItem(unitPrice = Money.krw(20000)),
            )

            // when
            val order = Order.of(
                userId = userId,
                totalAmount = totalAmount,
                status = status,
                orderItems = orderItems,
            )

            // then
            assertThat(order.userId).isEqualTo(userId)
            assertThat(order.totalAmount).isEqualTo(totalAmount)
            assertThat(order.status).isEqualTo(status)
            assertThat(order.orderItems).hasSize(2)
        }

        @DisplayName("총 금액이 음수일 때 예외가 발생한다")
        @Test
        fun `throws exception when total amount is negative`() {
            // given
            val negativeAmount = Money.krw(-1000)
            val orderItems = mutableListOf(createOrderItem())

            // when
            val exception = assertThrows<CoreException> {
                Order.of(
                    userId = 1L,
                    totalAmount = negativeAmount,
                    status = OrderStatus.PAID,
                    orderItems = orderItems,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("주문 금액은 0 이상이어야 합니다.")
        }

        @DisplayName("주문 상품이 비어있을 때 예외가 발생한다")
        @Test
        fun `throws exception when order items is empty`() {
            // given
            val emptyOrderItems = mutableListOf<OrderItem>()

            // when
            val exception = assertThrows<CoreException> {
                Order.of(
                    userId = 1L,
                    totalAmount = Money.krw(10000),
                    status = OrderStatus.PAID,
                    orderItems = emptyOrderItems,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("주문 상품이 없을 수 없습니다.")
        }
    }

    @DisplayName("주문 paid 테스트")
    @Nested
    inner class Paid {

        @DisplayName("주문 상품들의 (단가 × 수량) 합계로 총 금액이 계산된다")
        @Test
        fun `calculate total amount from order items unit prices multiplied by quantity`() {
            // given
            val userId = 1L
            val orderItems = mutableListOf(
                createOrderItem(unitPrice = Money.krw(30000), quantity = 2),
                createOrderItem(unitPrice = Money.krw(20000), quantity = 3),
                createOrderItem(unitPrice = Money.krw(10000), quantity = 1),
            )
            val expectedTotalAmount = Money.krw(130000)

            // when
            val order = Order.paid(userId, orderItems)

            // then
            assertThat(order.totalAmount).isEqualTo(expectedTotalAmount)
        }

        @DisplayName("주문 상태가 PAID로 설정된다")
        @Test
        fun `set order status to PAID`() {
            // given
            val userId = 1L
            val orderItems = mutableListOf(createOrderItem())

            // when
            val order = Order.paid(userId, orderItems)

            // then
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
        }

        @DisplayName("주문 상품이 주문에 포함된다")
        @Test
        fun `include order items in order`() {
            // given
            val userId = 1L
            val orderItems = mutableListOf(
                createOrderItem(productId = 1L),
                createOrderItem(productId = 2L),
            )

            // when
            val order = Order.paid(userId, orderItems)

            // then
            assertThat(order.orderItems).hasSize(2)
            assertThat(order.orderItems[0].productId).isEqualTo(1L)
            assertThat(order.orderItems[1].productId).isEqualTo(2L)
        }
    }

    private fun createOrderItem(
        productId: Long = 1L,
        quantity: Int = 1,
        productName: String = "테스트 상품",
        unitPrice: Money = Money.krw(10000),
    ): OrderItem {
        return OrderItem.create(
            productId = productId,
            quantity = quantity,
            productName = productName,
            unitPrice = unitPrice,
        )
    }
}
