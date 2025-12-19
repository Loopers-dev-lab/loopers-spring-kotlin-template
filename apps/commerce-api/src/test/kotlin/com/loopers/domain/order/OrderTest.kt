package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertAll
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
    }

    @DisplayName("place 생성 테스트")
    @Nested
    inner class Place {

        @DisplayName("빈 주문이 PLACED 상태로 생성된다")
        @Test
        fun `create empty order with PLACED status`() {
            // given
            val userId = 1L

            // when
            val order = Order.place(userId)

            // then
            assertThat(order.userId).isEqualTo(userId)
            assertThat(order.totalAmount).isEqualTo(Money.ZERO_KRW)
            assertThat(order.status).isEqualTo(OrderStatus.PLACED)
            assertThat(order.orderItems).isEmpty()
        }
    }

    @DisplayName("addOrderItem 테스트")
    @Nested
    inner class AddOrderItem {

        @DisplayName("주문에 상품을 추가할 수 있다")
        @Test
        fun `add order item to order`() {
            // given
            val order = createOrder(
                status = OrderStatus.PLACED,
                orderItems = mutableListOf(),
            )
            val productId = 100L
            val quantity = 2
            val productName = "맥북 프로"
            val unitPrice = Money.krw(2000000)

            // when
            order.addOrderItem(productId, quantity, productName, unitPrice)

            // then
            order.orderItems.first().run {
                assertAll(
                    { assertThat(this.productId).isEqualTo(productId) },
                    { assertThat(this.quantity).isEqualTo(quantity) },
                    { assertThat(this.productName).isEqualTo(productName) },
                    { assertThat(this.unitPrice).isEqualTo(unitPrice) },
                )
            }
        }

        @DisplayName("여러 상품을 순차적으로 추가할 수 있다")
        @Test
        fun `add multiple order items sequentially`() {
            // given
            val order = createOrder(
                status = OrderStatus.PLACED,
                orderItems = mutableListOf(),
            )

            // when
            addOrderItem(
                productId = 1L,
                order = order,
            )
            addOrderItem(
                productId = 2L,
                order = order,
            )
            addOrderItem(
                productId = 3L,
                order = order,
            )
            // then
            assertThat(
                order
                    .orderItems
                    .map { it.productId },
            ).containsExactly(1L, 2L, 3L)
        }

        @DisplayName("상품 추가 시 총 금액이 계산된다")
        @Test
        fun `calculate total amount when adding order items`() {
            // given
            val order = createOrder(
                status = OrderStatus.PLACED,
                orderItems = mutableListOf(),
            )

            // when
            addOrderItem(
                order = order,
                unitPrice = Money.krw(10000),
            )
            addOrderItem(
                order = order,
                unitPrice = Money.krw(5000),
            )

            // then
            assertAll(
                { assertThat(order.orderItems).hasSize(2) },
                { assertThat(order.totalAmount).isEqualTo(Money.krw(15000)) },
            )
        }

        @DisplayName("PLACED 상태가 아닐 때 상품 추가 시 예외가 발생한다")
        @Test
        fun `throws exception when adding item to non-PLACED order`() {
            // given
            val order = createOrder(status = OrderStatus.PAID)

            // when
            val exception = assertThrows<CoreException> {
                addOrderItem(order = order)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("PLACED 상태에서만 상품을 추가할 수 있습니다.")
        }
    }

    @DisplayName("pay 테스트")
    @Nested
    inner class Pay {

        @DisplayName("PLACED 상태의 주문이 PAID로 전환된다")
        @Test
        fun `transition from PLACED to PAID`() {
            // given
            val order = createOrder(
                status = OrderStatus.PLACED,
                totalAmount = Money.krw(20000),
            )

            // when
            order.pay()

            // then
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
        }

        @DisplayName("이미 PAID 상태에서 예외가 발생하지 않는다")
        @Test
        fun `order does nothing when pay on PAID`() {
            // given
            val order = createOrder(status = OrderStatus.PAID)

            // when
            order.pay()

            // then
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
        }

        @DisplayName("CANCELLED 상태에서 예외가 발생한다")
        @Test
        fun `throws exception when status is CANCELLED`() {
            // given
            val order = createOrder(status = OrderStatus.CANCELLED)

            // when
            val exception = assertThrows<CoreException> {
                order.pay()
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("주문이 완료되지 않았습니다.")
        }

        @DisplayName("주문 상품이 없을 때 예외가 발생한다")
        @Test
        fun `throws exception when order items is empty`() {
            // given
            val order = createOrder(
                status = OrderStatus.PLACED,
                orderItems = mutableListOf(),
            )

            // when
            val exception = assertThrows<CoreException> {
                order.pay()
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("주문 상품이 없을 수 없습니다.")
        }

        @DisplayName("총 금액이 0 이하일 때 예외가 발생한다")
        @Test
        fun `throws exception when total amount is zero or negative`() {
            // given
            val order = createOrder(
                status = OrderStatus.PLACED,
                totalAmount = Money.ZERO_KRW,
            )

            // when
            val exception = assertThrows<CoreException> {
                order.pay()
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("주문 금액은 0보다 커야 합니다.")
        }

        @DisplayName("pay() 성공 시 OrderPaidEventV1이 등록된다")
        @Test
        fun `pay() success registers OrderPaidEventV1`() {
            // given
            val order = createOrder(
                status = OrderStatus.PLACED,
                totalAmount = Money.krw(20000),
            )

            // when
            order.pay()

            // then
            val events = order.pollEvents()
            assertThat(events).hasSize(1)
            assertThat(events[0]).isInstanceOf(OrderPaidEventV1::class.java)
        }

        @DisplayName("이미 PAID 상태에서 pay() 호출 시 이벤트가 등록되지 않는다 (멱등성)")
        @Test
        fun `pay() when already PAID does not register event`() {
            // given
            val order = createOrder(status = OrderStatus.PAID)

            // when
            order.pay()

            // then
            val events = order.pollEvents()
            assertThat(events).isEmpty()
        }
    }

    @DisplayName("cancel 테스트")
    @Nested
    inner class Cancel {

        @DisplayName("PLACED 상태의 주문이 CANCELLED로 전환된다")
        @Test
        fun `transition from PLACED to CANCELLED`() {
            // given
            val order = createOrder(status = OrderStatus.PLACED)

            // when
            order.cancel()

            // then
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        }

        @DisplayName("cancel 성공 시 OrderCanceledEventV1이 등록된다")
        @Test
        fun `registers OrderCanceledEventV1 when cancel succeeds`() {
            // given
            val order = createOrder(status = OrderStatus.PLACED)

            // when
            order.cancel()

            // then
            val events = order.pollEvents()
            assertThat(events).hasSize(1)
            assertThat(events[0]).isInstanceOf(OrderCanceledEventV1::class.java)
        }

        @DisplayName("PAID 상태에서 cancel() 호출 시 예외가 발생한다")
        @Test
        fun `throws exception when cancel is called from PAID status`() {
            // given
            val order = createOrder(status = OrderStatus.PAID)

            // when
            val exception = assertThrows<CoreException> {
                order.cancel()
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("주문 대기 상태에서만 취소할 수 있습니다")
        }

        @DisplayName("이미 CANCELLED 상태인 주문에 cancel() 호출해도 예외가 발생하지 않는다 (멱등성)")
        @Test
        fun `cancel() on CANCELLED order does nothing (idempotency)`() {
            // given
            val order = createOrder(status = OrderStatus.CANCELLED)

            // when
            order.cancel()

            // then
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
            // 이벤트가 등록되지 않아야 함
            val events = order.pollEvents()
            assertThat(events).isEmpty()
        }
    }

    @DisplayName("pollEvents 테스트")
    @Nested
    inner class PollEvents {

        @DisplayName("초기 상태에서 pollEvents는 빈 리스트를 반환한다")
        @Test
        fun `pollEvents returns empty list initially`() {
            // given
            val order = createOrder()

            // when
            val events = order.pollEvents()

            // then
            assertThat(events).isEmpty()
        }

        @DisplayName("pollEvents 호출 후 두 번째 호출은 빈 리스트를 반환한다")
        @Test
        fun `pollEvents clears list after returning`() {
            // given
            val order = createOrder()
            order.pollEvents() // first call

            // when
            val secondEvents = order.pollEvents()

            // then
            assertThat(secondEvents).isEmpty()
        }
    }

    private fun addOrderItem(
        productId: Long = 1L,
        quantity: Int = 1,
        productName: String = "테스트 상품",
        unitPrice: Money = Money.krw(10000),
        order: Order,
    ) {
        order.addOrderItem(productId, quantity, productName, unitPrice)
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
        totalAmount: Money = Money.krw(10000),
        status: OrderStatus = OrderStatus.PLACED,
        orderItems: MutableList<OrderItem> = mutableListOf(createOrderItem()),
    ): Order {
        return Order.of(
            userId = userId,
            totalAmount = totalAmount,
            status = status,
            orderItems = orderItems,
        )
    }
}
