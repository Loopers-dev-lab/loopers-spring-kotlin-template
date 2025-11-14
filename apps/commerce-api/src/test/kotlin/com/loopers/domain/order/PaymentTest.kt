package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
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
            val order = createOrder(userId = userId)

            // when
            val payment = Payment.paid(
                userId = userId,
                order = order,
                usedPoint = order.totalAmount,
            )

            // then
            assertThat(payment.orderId).isEqualTo(order.id)
            assertThat(payment.userId).isEqualTo(userId)
            assertThat(payment.totalAmount).isEqualTo(order.totalAmount)
            assertThat(payment.usedPoint).isEqualTo(order.totalAmount)
        }

        @DisplayName("결제 상태가 PAID로 설정된다")
        @Test
        fun `have paid status when paid`() {
            // given
            val order = createOrder()

            // when
            val payment = createPayment(order = order)

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("사용 포인트가 주문 금액과 정확히 일치하면 결제가 생성된다")
        @Test
        fun `create payed payment when used point exactly matches total amount`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )

            // when
            val payment = createPayment(order = order)

            // then
            assertThat(payment.usedPoint).isEqualTo(order.totalAmount)
            assertThat(payment.totalAmount).isEqualTo(order.totalAmount)
        }

        @DisplayName("사용 포인트가 주문 금액보다 부족하면 예외가 발생한다")
        @Test
        fun `throws exception when used point is less than total amount`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )
            val insufficientPoint = Money.krw(5000)

            // when
            val exception = assertThrows<CoreException> {
                Payment.paid(
                    userId = 1L,
                    order = order,
                    usedPoint = insufficientPoint,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("사용 포인트가 주문 금액과 일치하지 않습니다.")
        }

        @DisplayName("사용 포인트가 주문 금액을 초과하면 예외가 발생한다")
        @Test
        fun `throws exception when used point exceeds total amount`() {
            // given
            val order = createOrder(
                orderItems = mutableListOf(
                    createOrderItem(unitPrice = Money.krw(10000)),
                ),
            )
            val excessPoint = Money.krw(15000)

            // when
            val exception = assertThrows<CoreException> {
                Payment.paid(
                    userId = 1L,
                    order = order,
                    usedPoint = excessPoint,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("사용 포인트가 주문 금액과 일치하지 않습니다.")
        }

        @DisplayName("사용 포인트가 음수이면 예외가 발생한다")
        @Test
        fun `throws exception when used point is negative`() {
            // given
            val order = createOrder()
            val negativePoint = Money.krw(-1000)

            // when
            val exception = assertThrows<CoreException> {
                Payment.paid(
                    userId = 1L,
                    order = order,
                    usedPoint = negativePoint,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("사용 포인트는 0 이상이어야 합니다.")
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

    private fun createPayment(
        userId: Long = 1L,
        order: Order = createOrder(),
    ): Payment {
        return Payment.paid(
            userId = userId,
            order = order,
            usedPoint = order.totalAmount,
        )
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
