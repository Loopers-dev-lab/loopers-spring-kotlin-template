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

/**
 * OrderService 통합 테스트
 *
 * 검증 범위:
 * - 주문 생성 오케스트레이션 (Order + OrderItem 저장)
 * - 트랜잭션 원자성
 */
@SpringBootTest
@DisplayName("OrderService 통합 테스트")
class OrderServiceIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
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
