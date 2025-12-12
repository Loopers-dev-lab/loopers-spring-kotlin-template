package com.loopers.domain.order

import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents

/**
 * OrderService 통합 테스트
 *
 * 검증 범위:
 * - 주문 생성 오케스트레이션 (Order + OrderItem 저장)
 * - 트랜잭션 원자성
 * - 도메인 이벤트 발행 (OrderCreatedEventV1, OrderCanceledEventV1)
 */
@SpringBootTest
@RecordApplicationEvents
@DisplayName("OrderService 통합 테스트")
class OrderServiceIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("place")
    inner class Place {

        @Test
        @DisplayName("주문을 생성하면 PLACED 상태로 저장된다")
        fun `creates order with PLACED status`() {
            // given
            val command = placeOrderCommand(userId = 1L)

            // when
            val savedOrder = orderService.place(command)

            // then
            val foundOrder = orderRepository.findById(savedOrder.id)
            assertThat(foundOrder).isNotNull
            assertThat(foundOrder!!.status).isEqualTo(OrderStatus.PLACED)
        }

        @Test
        @DisplayName("여러 상품을 포함한 주문을 생성할 수 있다")
        fun `creates order with multiple items`() {
            // given
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                items = listOf(
                    placeOrderItem(productId = 1L),
                    placeOrderItem(productId = 2L),
                    placeOrderItem(productId = 3L),
                ),
            )

            // when
            val savedOrder = orderService.place(command)

            // then
            val foundOrder = orderRepository.findById(savedOrder.id)
            assertThat(foundOrder).isNotNull
            assertThat(foundOrder!!.orderItems).hasSize(3)
        }

        @Test
        @DisplayName("주문 생성 시 OrderCreatedEventV1을 발행한다")
        fun `publishes OrderCreatedEventV1 when order is placed`() {
            // given
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                items = listOf(
                    placeOrderItem(productId = 100L, quantity = 2),
                    placeOrderItem(productId = 200L, quantity = 3),
                ),
            )

            // when
            val savedOrder = orderService.place(command)

            // then
            val events = applicationEvents.stream(OrderCreatedEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.orderId).isEqualTo(savedOrder.id)
            assertThat(event.orderItems).hasSize(2)
            assertThat(event.orderItems[0].productId).isEqualTo(100L)
            assertThat(event.orderItems[0].quantity).isEqualTo(2)
            assertThat(event.orderItems[1].productId).isEqualTo(200L)
            assertThat(event.orderItems[1].quantity).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    inner class CancelOrder {

        @Test
        @DisplayName("주문 취소 시 OrderCanceledEventV1을 발행한다")
        fun `publishes OrderCanceledEventV1 when order is canceled`() {
            // given
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                items = listOf(
                    placeOrderItem(productId = 100L, quantity = 2),
                    placeOrderItem(productId = 200L, quantity = 3),
                ),
            )
            val savedOrder = orderService.place(command)

            // Clear events from place() to focus on cancelOrder() events
            applicationEvents.clear()

            // when
            val canceledOrder = orderService.cancelOrder(savedOrder.id)

            // then
            val events = applicationEvents.stream(OrderCanceledEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.orderId).isEqualTo(canceledOrder.id)
            assertThat(event.orderItems).hasSize(2)
            assertThat(event.orderItems[0].productId).isEqualTo(100L)
            assertThat(event.orderItems[0].quantity).isEqualTo(2)
            assertThat(event.orderItems[1].productId).isEqualTo(200L)
            assertThat(event.orderItems[1].quantity).isEqualTo(3)
        }
    }

    private fun placeOrderCommand(userId: Long = 1L) = OrderCommand.PlaceOrder(
        userId = userId,
        items = listOf(placeOrderItem()),
    )

    private fun placeOrderItem(
        productId: Long = 1L,
        quantity: Int = 1,
        currentPrice: Money = Money.krw(10000),
    ) = OrderCommand.PlaceOrderItem(
        productId = productId,
        productName = "테스트 상품",
        quantity = quantity,
        currentPrice = currentPrice,
    )
}
