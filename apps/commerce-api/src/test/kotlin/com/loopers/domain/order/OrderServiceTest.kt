package com.loopers.domain.order

import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.domain.product.StockService
import com.loopers.fixtures.createTestBrand
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderServiceTest {
    private val orderRepository: OrderRepository = mockk(relaxed = true)
    private val stockService: StockService = mockk(relaxed = true)

    private val orderService = OrderService(orderRepository, stockService)

    @Test
    fun `주문을 생성할 수 있다`() {
        // given
        val userId = 1L
        val brand = createTestBrand(id = 1L, name = "Test Brand")
        val orderItem = OrderItem(
            productId = 100L,
            productName = "운동화",
            brandId = brand.id,
            brandName = brand.name,
            brandDescription = brand.description,
            quantity = 2,
            priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
        )

        every { orderRepository.save(any()) } answers { firstArg() }

        // when
        val order = orderService.createOrder(userId, listOf(orderItem))

        // then
        assertThat(order.userId).isEqualTo(userId)
        assertThat(order.items).hasSize(1)
        assertThat(order.items[0].quantity).isEqualTo(2)
        verify { orderRepository.save(any()) }
    }

    @Test
    fun `여러 주문 아이템으로 주문을 생성할 수 있다`() {
        // given
        val userId = 1L
        val brand = createTestBrand(id = 1L, name = "Test Brand")
        val orderItem1 = OrderItem(
            productId = 100L,
            productName = "운동화",
            brandId = brand.id,
            brandName = brand.name,
            brandDescription = brand.description,
            quantity = 1,
            priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
        )
        val orderItem2 = OrderItem(
            productId = 101L,
            productName = "티셔츠",
            brandId = brand.id,
            brandName = brand.name,
            brandDescription = brand.description,
            quantity = 2,
            priceAtOrder = Price(BigDecimal("50000"), Currency.KRW),
        )

        every { orderRepository.save(any()) } answers { firstArg() }

        // when
        val order = orderService.createOrder(userId, listOf(orderItem1, orderItem2))

        // then
        assertThat(order.items).hasSize(2)
        assertThat(order.calculateTotalAmount().amount).isEqualTo(BigDecimal("200000"))
    }

    @Test
    fun `주문을 취소하면 재고가 복구된다`() {
        // given
        val orderId = 1L
        val userId = 1L
        val brand = createTestBrand(id = 1L, name = "Test Brand")
        val orderItem1 = OrderItem(
            productId = 100L,
            productName = "운동화",
            brandId = brand.id,
            brandName = brand.name,
            brandDescription = brand.description,
            quantity = 2,
            priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
        )
        val orderItem2 = OrderItem(
            productId = 101L,
            productName = "티셔츠",
            brandId = brand.id,
            brandName = brand.name,
            brandDescription = brand.description,
            quantity = 3,
            priceAtOrder = Price(BigDecimal("50000"), Currency.KRW),
        )
        val order = Order(userId = userId, items = listOf(orderItem1, orderItem2))

        every { orderRepository.findByIdWithLock(orderId) } returns order
        every { orderRepository.save(any()) } answers { firstArg() }
        every { stockService.increaseStock(any(), any()) } returns mockk(relaxed = true)

        // when
        val result = orderService.cancelOrder(orderId, userId)

        // then
        assertThat(result.status).isEqualTo(OrderStatus.CANCELLED)
        verify(exactly = 1) { stockService.increaseStock(100L, 2) }
        verify(exactly = 1) { stockService.increaseStock(101L, 3) }
    }

    @Test
    fun `본인의 주문이 아니면 취소할 수 없다`() {
        // given
        val orderId = 1L
        val orderOwnerId = 1L
        val requestUserId = 2L
        val brand = createTestBrand(id = 1L, name = "Test Brand")
        val orderItem = OrderItem(
            productId = 100L,
            productName = "운동화",
            brandId = brand.id,
            brandName = brand.name,
            brandDescription = brand.description,
            quantity = 2,
            priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
        )
        val order = Order(userId = orderOwnerId, items = listOf(orderItem))

        every { orderRepository.findByIdWithLock(orderId) } returns order

        // when & then
        assertThatThrownBy {
            orderService.cancelOrder(orderId, requestUserId)
        }
            .isInstanceOf(CoreException::class.java)
            .hasMessageContaining("본인의 주문만 취소할 수 있습니다")

        verify(exactly = 0) { stockService.increaseStock(any(), any()) }
    }

    @Test
    fun `존재하지 않는 주문은 취소할 수 없다`() {
        // given
        val orderId = 999L
        val userId = 1L

        every { orderRepository.findByIdWithLock(orderId) } returns null

        // when & then
        assertThatThrownBy {
            orderService.cancelOrder(orderId, userId)
        }
            .isInstanceOf(CoreException::class.java)
            .hasMessageContaining("주문을 찾을 수 없습니다")

        verify(exactly = 0) { stockService.increaseStock(any(), any()) }
    }

    @Test
    fun `이미 확정된 주문은 취소할 수 없다`() {
        // given
        val orderId = 1L
        val userId = 1L
        val brand = createTestBrand(id = 1L, name = "Test Brand")
        val orderItem = OrderItem(
            productId = 100L,
            productName = "운동화",
            brandId = brand.id,
            brandName = brand.name,
            brandDescription = brand.description,
            quantity = 2,
            priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
        )
        val order = Order(userId = userId, items = listOf(orderItem))
        order.confirm() // 주문 확정

        every { orderRepository.findByIdWithLock(orderId) } returns order

        // when & then
        assertThatThrownBy {
            orderService.cancelOrder(orderId, userId)
        }
            .isInstanceOf(CoreException::class.java)
            .hasMessageContaining("이미 확정된 주문은 취소할 수 없습니다")

        verify(exactly = 0) { stockService.increaseStock(any(), any()) }
    }
}
