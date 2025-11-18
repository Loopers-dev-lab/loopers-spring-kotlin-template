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

        @DisplayName("주문을 생성하면 Order와 Payment가 모두 저장된다")
        @Test
        fun `save order and payment when place order`() {
            // given
            val userId = 1L
            val command = createPlaceOrderCommand(userId = userId)

            // when
            val savedOrder = orderService.place(command)

            // then
            val foundOrder = orderRepository.findById(savedOrder.id)
            val foundPayment = paymentRepository.findByOrderId(savedOrder.id)

            assertAll(
                { assertThat(foundOrder).isNotNull() },
                { assertThat(foundPayment).isNotNull() },
                { assertThat(foundPayment?.orderId).isEqualTo(savedOrder.id) },
            )
        }

        @DisplayName("주문 금액이 올바르게 계산되어 PAID 상태로 저장된다")
        @Test
        fun `calculate and save total amount correctly`() {
            // given
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                usePoint = Money.krw(50000),
                items = listOf(
                    createPlaceOrderItem(quantity = 2, currentPrice = Money.krw(10000)),
                    createPlaceOrderItem(quantity = 3, currentPrice = Money.krw(10000)),
                ),
            )

            // when
            val savedOrder = orderService.place(command)

            // then
            assertThat(savedOrder.totalAmount).isEqualTo(Money.krw(50000))
            assertThat(savedOrder.status).isEqualTo(OrderStatus.PAID)
        }

        @DisplayName("주문 상품이 OrderItem으로 저장된다")
        @Test
        fun `save order items correctly`() {
            // given
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                usePoint = Money.krw(20000),
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

        @DisplayName("결제 정보가 올바르게 생성된다")
        @Test
        fun `create payment with correct information`() {
            // given
            val userId = 1L
            val usePoint = Money.krw(30000)
            val command = OrderCommand.PlaceOrder(
                userId = userId,
                usePoint = usePoint,
                items = listOf(
                    createPlaceOrderItem(quantity = 3, currentPrice = Money.krw(10000)),
                ),
            )

            // when
            val savedOrder = orderService.place(command)

            // then
            val foundPayment = paymentRepository.findByOrderId(savedOrder.id)
            assertAll(
                { assertThat(foundPayment?.userId).isEqualTo(userId) },
                { assertThat(foundPayment?.totalAmount).isEqualTo(Money.krw(30000)) },
                { assertThat(foundPayment?.usedPoint).isEqualTo(usePoint) },
                { assertThat(foundPayment?.status).isEqualTo(PaymentStatus.PAID) },
            )
        }
    }

    private fun createPlaceOrderCommand(
        userId: Long = 1L,
        usePoint: Money = Money.krw(10000),
    ): OrderCommand.PlaceOrder {
        return OrderCommand.PlaceOrder(
            userId = userId,
            usePoint = usePoint,
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
}
