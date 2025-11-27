package com.loopers.domain.order

import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OrderServiceIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문 생성 통합테스트")
    @Nested
    inner class Place {

        @DisplayName("주문을 생성하면 Order가 PLACED 상태로 저장된다")
        @Test
        fun `save order when place order`() {
            // given
            val userId = 1L
            val command = createPlaceOrderCommand(userId = userId)

            // when
            val savedOrder = orderService.place(command)

            // then
            val foundOrder = orderRepository.findById(savedOrder.id)

            assertAll(
                { assertThat(foundOrder).isNotNull() },
                { assertThat(foundOrder?.userId).isEqualTo(userId) },
                { assertThat(foundOrder?.status).isEqualTo(OrderStatus.PLACED) },
            )
        }

        @DisplayName("주문 금액이 올바르게 계산된다")
        @Test
        fun `calculate total amount correctly`() {
            // given
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                items = listOf(
                    createPlaceOrderItem(quantity = 2, currentPrice = Money.krw(10000)),
                    createPlaceOrderItem(quantity = 3, currentPrice = Money.krw(10000)),
                ),
            )

            // when
            val savedOrder = orderService.place(command)

            // then
            assertThat(savedOrder.totalAmount).isEqualTo(Money.krw(50000))
        }

        @DisplayName("주문 상품이 OrderItem으로 저장된다")
        @Test
        fun `save order items correctly`() {
            // given
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                items = listOf(
                    createPlaceOrderItem(
                        productId = 1L,
                        quantity = 2,
                        currentPrice = Money.krw(10000),
                    ),
                ),
            )

            // when
            val savedOrder = orderService.place(command)

            // then
            val foundOrder = orderRepository.findById(savedOrder.id)
            assertAll(
                { assertThat(foundOrder?.orderItems).hasSize(1) },
                { assertThat(foundOrder?.orderItems?.get(0)?.productId).isEqualTo(1L) },
                { assertThat(foundOrder?.orderItems?.get(0)?.quantity).isEqualTo(2) },
                { assertThat(foundOrder?.orderItems?.get(0)?.unitPrice).isEqualTo(Money.krw(10000)) },
            )
        }
    }

    @DisplayName("결제 처리 통합테스트")
    @Nested
    inner class Pay {

        @DisplayName("주문을 결제하면 Payment가 생성되고 Order 상태가 PAID로 변경된다")
        @Test
        fun `create payment and update order status when pay`() {
            // given
            val userId = 1L
            val order = createOrder(userId = userId)

            val payCommand = OrderCommand.Pay(
                orderId = order.id,
                userId = userId,
                usePoint = Money.krw(10000),
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            )

            // when
            val payment = orderService.pay(payCommand)

            // then
            val updatedOrder = orderRepository.findById(order.id)
            val foundPayment = paymentRepository.findByOrderId(order.id)

            assertAll(
                { assertThat(updatedOrder?.status).isEqualTo(OrderStatus.PAID) },
                { assertThat(foundPayment).isNotNull() },
                { assertThat(foundPayment?.orderId).isEqualTo(order.id) },
                { assertThat(foundPayment?.userId).isEqualTo(userId) },
                { assertThat(foundPayment?.totalAmount).isEqualTo(Money.krw(10000)) },
                { assertThat(foundPayment?.usedPoint).isEqualTo(Money.krw(10000)) },
                { assertThat(foundPayment?.status).isEqualTo(PaymentStatus.PAID) },
            )
        }

        @DisplayName("결제 시 쿠폰 할인이 적용된다")
        @Test
        fun `apply coupon discount when pay`() {
            // given
            val userId = 1L
            val order = createOrder(userId = userId)

            val payCommand = OrderCommand.Pay(
                orderId = order.id,
                userId = userId,
                usePoint = Money.krw(5000),
                issuedCouponId = 100L,
                couponDiscount = Money.krw(5000),
            )

            // when
            val payment = orderService.pay(payCommand)

            // then
            assertAll(
                { assertThat(payment.totalAmount).isEqualTo(Money.krw(10000)) },
                { assertThat(payment.usedPoint).isEqualTo(Money.krw(5000)) },
                { assertThat(payment.couponDiscount).isEqualTo(Money.krw(5000)) },
                { assertThat(payment.issuedCouponId).isEqualTo(100L) },
            )
        }
    }

    private fun createPlaceOrderCommand(
        userId: Long = 1L,
    ): OrderCommand.PlaceOrder {
        return OrderCommand.PlaceOrder(
            userId = userId,
            items = listOf(
                createPlaceOrderItem(),
            ),
        )
    }

    private fun createPlaceOrderItem(
        productId: Long = 1L,
        quantity: Int = 1,
        currentPrice: Money = Money.krw(10000),
    ): OrderCommand.PlaceOrderItem {
        return OrderCommand.PlaceOrderItem(
            productId = productId,
            productName = "테스트 상품",
            quantity = quantity,
            currentPrice = currentPrice,
        )
    }

    private fun createOrder(
        userId: Long = 1L,
        totalAmount: Money = Money.krw(10000),
        status: OrderStatus = OrderStatus.PLACED,
    ): Order {
        val orderItem = OrderItem.create(
            productId = 1L,
            quantity = 1,
            productName = "테스트 상품",
            unitPrice = Money.krw(10000),
        )
        val order = Order.of(
            userId = userId,
            totalAmount = totalAmount,
            status = status,
            orderItems = mutableListOf(orderItem),
        )
        return orderRepository.save(order)
    }
}
