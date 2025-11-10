package com.loopers.domain.order

import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.fixtures.createTestBrand
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderServiceTest {
    private val orderRepository: OrderRepository = mockk(relaxed = true)

    private val orderService = OrderService(orderRepository)

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
}
