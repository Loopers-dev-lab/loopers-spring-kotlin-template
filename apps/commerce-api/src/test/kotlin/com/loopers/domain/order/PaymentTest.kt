package com.loopers.domain.order

import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class PaymentTest {

    @DisplayName("paid 테스트")
    @Nested
    inner class Paid {

        @DisplayName("주문 정보로 결제가 생성된다")
        @Test
        fun `create payment when provide order`() {
            // given
            val userId = 1L
            val orderItems = mutableListOf(
                createOrderItem(unitPrice = Money.krw(30000)),
                createOrderItem(unitPrice = Money.krw(20000)),
            )
            val order = createOrder(
                userId = userId,
                orderItems = orderItems,
            )
            val usedPoint = Money.krw(5000)

            // when
            val payment = Payment.paid(
                userId = userId,
                order = order,
                usedPoint = usedPoint,
            )

            // then
            assertThat(payment.orderId).isEqualTo(order.id)
            assertThat(payment.userId).isEqualTo(userId)
            assertThat(payment.totalAmount).isEqualTo(order.totalAmount)
            assertThat(payment.usedPoint).isEqualTo(usedPoint)
        }

        @DisplayName("결제 상태가 PAID로 설정된다")
        @Test
        fun `have paid status when paid`() {
            // given
            val userId = 1L
            val order = createOrder()
            val usedPoint = Money.krw(1000)

            // when
            val payment = Payment.paid(
                userId = userId,
                order = order,
                usedPoint = usedPoint,
            )

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("포인트를 사용하지 않으면 usedPoint가 0원이다")
        @Test
        fun `used point is zero when no point is used`() {
            // given
            val userId = 1L
            val order = createOrder()
            val noPoint = Money.ZERO_KRW

            // when
            val payment = Payment.paid(
                userId = userId,
                order = order,
                usedPoint = noPoint,
            )

            // then
            assertThat(payment.usedPoint).isEqualTo(Money.ZERO_KRW)
        }
    }

    @DisplayName("of 팩토리 메서드 테스트")
    @Nested
    inner class Of {

        @DisplayName("직접 값을 지정하여 결제가 생성된다")
        @Test
        fun `create payment when provide specified values`() {
            // given
            val orderId = 1L
            val userId = 1L
            val totalAmount = Money.krw(50000)
            val usedPoint = Money.krw(5000)
            val status = PaymentStatus.PAID

            // when
            val payment = Payment.of(
                orderId = orderId,
                userId = userId,
                totalAmount = totalAmount,
                usedPoint = usedPoint,
                status = status,
            )

            // then
            assertThat(payment.orderId).isEqualTo(orderId)
            assertThat(payment.userId).isEqualTo(userId)
            assertThat(payment.totalAmount).isEqualTo(totalAmount)
            assertThat(payment.usedPoint).isEqualTo(usedPoint)
            assertThat(payment.status).isEqualTo(status)
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

    private fun createOrder(
        userId: Long = 1L,
        orderItems: MutableList<OrderItem> = mutableListOf(createOrderItem()),
    ): Order {
        return Order.paid(userId, orderItems)
    }
}
