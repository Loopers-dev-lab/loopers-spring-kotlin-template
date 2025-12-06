package com.loopers.domain.order

import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.fixtures.createTestOrder
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

class OrderQueryServiceTest {
    private val orderRepository = mockk<OrderRepository>()
    private val orderQueryService = OrderQueryService(orderRepository)

    @DisplayName("주문 목록을 조회할 때,")
    @Nested
    inner class GetOrders {
        @DisplayName("사용자의 주문 목록을 반환한다")
        @Test
        fun getOrders_returnsUserOrders() {
            // given
            val userId = 1L
            val pageable = PageRequest.of(0, 20)
            val items = listOf(
                OrderItem(
                    productId = 100L,
                    productName = "운동화",
                    brandId = 1L,
                    brandName = "나이키",
                    brandDescription = null,
                    quantity = 1,
                    priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
                ),
            )
            val order = createTestOrder(id = 1L, userId = userId, items = items)
            val page = PageImpl(listOf(order))

            every { orderRepository.findByUserId(userId, pageable) } returns page

            // when
            val result = orderQueryService.getOrders(userId, pageable)

            // then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].userId).isEqualTo(userId)
            verify(exactly = 1) { orderRepository.findByUserId(userId, pageable) }
        }

        @DisplayName("주문이 없으면 빈 페이지를 반환한다")
        @Test
        fun getOrders_whenNoOrders_thenReturnsEmptyPage() {
            // given
            val userId = 1L
            val pageable = PageRequest.of(0, 20)
            val emptyPage = PageImpl<Order>(emptyList())

            every { orderRepository.findByUserId(userId, pageable) } returns emptyPage

            // when
            val result = orderQueryService.getOrders(userId, pageable)

            // then
            assertThat(result.content).isEmpty()
        }
    }

    @DisplayName("주문 상세를 조회할 때,")
    @Nested
    inner class GetOrderDetail {
        @DisplayName("자신의 주문이면 주문 상세를 반환한다")
        @Test
        fun getOrderDetail_whenOwnOrder_thenReturnsOrder() {
            // given
            val userId = 1L
            val orderId = 100L
            val items = listOf(
                OrderItem(
                    productId = 100L,
                    productName = "운동화",
                    brandId = 1L,
                    brandName = "나이키",
                    brandDescription = null,
                    quantity = 1,
                    priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
                ),
            )
            val order = createTestOrder(id = orderId, userId = userId, items = items)

            every { orderRepository.findById(orderId) } returns order

            // when
            val result = orderQueryService.getOrderDetail(userId, orderId)

            // then
            assertThat(result).isEqualTo(order)
            assertThat(result.userId).isEqualTo(userId)
            verify(exactly = 1) { orderRepository.findById(orderId) }
        }

        @DisplayName("주문이 존재하지 않으면 예외가 발생한다")
        @Test
        fun getOrderDetail_whenOrderNotFound_thenThrowsException() {
            // given
            val userId = 1L
            val orderId = 999L

            every { orderRepository.findById(orderId) } returns null

            // when & then
            val exception = assertThrows<CoreException> {
                orderQueryService.getOrderDetail(userId, orderId)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("주문을 찾을 수 없습니다")
            assertThat(exception.message).contains("999")
        }

        @DisplayName("다른 사용자의 주문이면 예외가 발생한다")
        @Test
        fun getOrderDetail_whenNotOwnOrder_thenThrowsException() {
            // given
            val userId = 1L
            val otherUserId = 2L
            val orderId = 100L
            val items = listOf(
                OrderItem(
                    productId = 100L,
                    productName = "운동화",
                    brandId = 1L,
                    brandName = "나이키",
                    brandDescription = null,
                    quantity = 1,
                    priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
                ),
            )
            val order = createTestOrder(id = orderId, userId = otherUserId, items = items)

            every { orderRepository.findById(orderId) } returns order

            // when & then
            val exception = assertThrows<CoreException> {
                orderQueryService.getOrderDetail(userId, orderId)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.FORBIDDEN)
            assertThat(exception.message).contains("다른 사용자의 주문입니다")
        }
    }
}
