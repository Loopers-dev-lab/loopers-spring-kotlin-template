package com.loopers.domain.order

import com.loopers.IntegrationTestSupport
import com.loopers.application.order.OrderCommand
import com.loopers.application.order.OrderItemCommand
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.math.BigDecimal

class OrderServiceTest(
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    private val databaseCleanUp: DatabaseCleanUp,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문")
    @Nested
    inner class Order {

        @DisplayName("주문이 성공적으로 저장된다.")
        @Test
        fun orderSuccess() {
            // arrange
            val userId = 1L
            val orderItemCommand1 = OrderItemCommand(
                productId = 1L,
                quantity = 2,
                productPrice = BigDecimal.valueOf(1000),
            )
            val orderItemCommand2 = OrderItemCommand(
                productId = 2L,
                quantity = 3,
                productPrice = BigDecimal.valueOf(500),
            )
            val orderCommand = OrderCommand(listOf(orderItemCommand1, orderItemCommand2))

            // act
            val savedOrder = orderService.order(userId, orderCommand)

            // assert
            assertAll(
                { assertThat(savedOrder.id).isNotNull() },
                { assertThat(savedOrder.refUserId).isEqualTo(userId) },
                { assertThat(savedOrder.orderItems).hasSize(2) },
                { assertThat(savedOrder.totalPrice.amount).isEqualByComparingTo(BigDecimal.valueOf(3500)) },
            )
        }

        @DisplayName("주문 아이템이 정확하게 저장된다.")
        @Test
        fun orderItemsSavedCorrectly() {
            // arrange
            val userId = 1L
            val orderItemCommand = OrderItemCommand(
                productId = 100L,
                quantity = 5,
                productPrice = BigDecimal.valueOf(2000),
            )
            val orderCommand = OrderCommand(listOf(orderItemCommand))

            // act
            val savedOrder = orderService.order(userId, orderCommand)

            // assert
            val orderItem = savedOrder.orderItems.first()
            assertAll(
                { assertThat(orderItem.refProductId).isEqualTo(100L) },
                { assertThat(orderItem.quantity).isEqualTo(5) },
                { assertThat(orderItem.productPrice.amount).isEqualByComparingTo(BigDecimal.valueOf(2000)) },
            )
        }

        @DisplayName("총 가격이 정확하게 계산된다.")
        @Test
        fun totalPriceCalculatedCorrectly() {
            // arrange
            val userId = 1L
            val orderCommand = OrderCommand(
                listOf(
                    OrderItemCommand(productId = 1L, quantity = 1, productPrice = BigDecimal.valueOf(100)),
                    OrderItemCommand(productId = 2L, quantity = 2, productPrice = BigDecimal.valueOf(200)),
                    OrderItemCommand(productId = 3L, quantity = 3, productPrice = BigDecimal.valueOf(300)),
                ),
            )

            // act
            val savedOrder = orderService.order(userId, orderCommand)

            // assert
            // (1 * 100) + (2 * 200) + (3 * 300) = 100 + 400 + 900 = 1400
            assertThat(savedOrder.totalPrice.amount).isEqualByComparingTo(BigDecimal.valueOf(1400))
        }

        @DisplayName("주문 아이템이 없는 경우, 총 가격은 0이다.")
        @Test
        fun totalPriceIsZero_whenNoOrderItems() {
            // arrange
            val userId = 1L
            val orderCommand = OrderCommand(emptyList())

            // act
            val savedOrder = orderService.order(userId, orderCommand)

            // assert
            assertAll(
                { assertThat(savedOrder.orderItems).isEmpty() },
                { assertThat(savedOrder.totalPrice.amount).isEqualByComparingTo(BigDecimal.ZERO) },
            )
        }
    }
}
