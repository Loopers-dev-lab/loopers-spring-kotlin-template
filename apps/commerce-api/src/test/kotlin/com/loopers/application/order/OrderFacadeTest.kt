package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderQueryService
import com.loopers.domain.order.OrderService
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

class OrderFacadeTest {
    private val orderService: OrderService = mockk()
    private val orderQueryService: OrderQueryService = mockk()

    private val orderFacade = OrderFacade(orderService, orderQueryService)

    private fun createTestOrder(orderId: Long, userId: Long): Order {
        val items = listOf(
            OrderItem(
                productId = 100L,
                productName = "운동화",
                brandId = 1L,
                brandName = "나이키",
                brandDescription = null,
                quantity = 2,
                priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
            ),
        )
        return Order(userId = userId, items = items).apply {
            val idField = Order::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, orderId)
        }
    }

    @Test
    fun `주문을 생성할 수 있다`() {
        // given
        val userId = 1L
        val request = OrderCreateRequest(
            items = listOf(
                OrderItemRequest(productId = 100L, quantity = 2),
            ),
        )
        val order = createTestOrder(1L, userId)

        every { orderService.createOrder(userId, any()) } returns order

        // when
        val result = orderFacade.createOrder(userId, request)

        // then
        assertThat(result.orderId).isEqualTo(1L)
        assertThat(result.userId).isEqualTo(userId)
        verify { orderService.createOrder(userId, any()) }
    }

    @Test
    fun `사용자의 주문 목록을 조회할 수 있다`() {
        // given
        val userId = 1L
        val pageable = PageRequest.of(0, 20)
        val order = createTestOrder(1L, userId)

        every { orderQueryService.getOrders(userId, pageable) } returns PageImpl(listOf(order))

        // when
        val result = orderFacade.getOrders(userId, pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].orderId).isEqualTo(1L)
    }

    @Test
    fun `주문 상세 정보를 조회할 수 있다`() {
        // given
        val userId = 1L
        val orderId = 1L
        val order = createTestOrder(orderId, userId)

        every { orderQueryService.getOrderDetail(userId, orderId) } returns order

        // when
        val result = orderFacade.getOrderDetail(userId, orderId)

        // then
        assertThat(result.orderId).isEqualTo(orderId)
        assertThat(result.userId).isEqualTo(userId)
        assertThat(result.items).hasSize(1)
    }

    @Test
    fun `존재하지 않는 주문 조회 시 예외가 발생한다`() {
        // given
        val userId = 1L
        val orderId = 999L

        every { orderQueryService.getOrderDetail(userId, orderId) } throws CoreException(
            com.loopers.support.error.ErrorType.NOT_FOUND,
            "주문을 찾을 수 없습니다: $orderId",
        )

        // when & then
        assertThatThrownBy {
            orderFacade.getOrderDetail(userId, orderId)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("주문을 찾을 수 없습니다")
    }

    @Test
    fun `다른 사용자의 주문 조회 시 예외가 발생한다`() {
        // given
        val userId = 1L
        val otherUserId = 2L
        val orderId = 1L

        every { orderQueryService.getOrderDetail(userId, orderId) } throws CoreException(
            com.loopers.support.error.ErrorType.FORBIDDEN,
            "다른 사용자의 주문입니다",
        )

        // when & then
        assertThatThrownBy {
            orderFacade.getOrderDetail(userId, orderId)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("다른 사용자의 주문입니다")
    }
}
