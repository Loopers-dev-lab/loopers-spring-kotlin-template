package com.loopers.interfaces.event

import com.loopers.domain.order.OrderCanceledEventV1
import com.loopers.domain.order.OrderCreatedEventV1
import com.loopers.domain.product.ProductCommand
import com.loopers.domain.product.ProductService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OrderEventListenerTest {
    private lateinit var productService: ProductService
    private lateinit var orderEventListener: OrderEventListener

    @BeforeEach
    fun setUp() {
        productService = mockk()
        orderEventListener = OrderEventListener(productService)
    }

    @Nested
    @DisplayName("onOrderCreated")
    inner class OnOrderCreated {
        @Test
        @DisplayName("productService.decreaseStocks()를 올바른 command로 호출한다")
        fun `calls productService decreaseStocks with correct command`() {
            // given
            val orderItems = listOf(
                OrderCreatedEventV1.OrderItemSnapshot(productId = 1L, quantity = 2),
                OrderCreatedEventV1.OrderItemSnapshot(productId = 2L, quantity = 3),
            )
            val event = OrderCreatedEventV1(
                orderId = 100L,
                orderItems = orderItems,
            )

            val commandSlot = slot<ProductCommand.DecreaseStocks>()
            every { productService.decreaseStocks(capture(commandSlot)) } just runs

            // when
            orderEventListener.onOrderCreated(event)

            // then
            verify(exactly = 1) { productService.decreaseStocks(any()) }

            val capturedCommand = commandSlot.captured
            assertThat(capturedCommand.units).hasSize(2)
            assertThat(capturedCommand.units[0].productId).isEqualTo(1L)
            assertThat(capturedCommand.units[0].amount).isEqualTo(2)
            assertThat(capturedCommand.units[1].productId).isEqualTo(2L)
            assertThat(capturedCommand.units[1].amount).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("onOrderCanceled")
    inner class OnOrderCanceled {
        @Test
        @DisplayName("productService.increaseStocks()를 올바른 command로 호출한다")
        fun `calls productService increaseStocks with correct command`() {
            // given
            val orderItems = listOf(
                OrderCreatedEventV1.OrderItemSnapshot(productId = 1L, quantity = 2),
                OrderCreatedEventV1.OrderItemSnapshot(productId = 2L, quantity = 3),
            )
            val event = OrderCanceledEventV1(
                orderId = 100L,
                orderItems = orderItems,
            )

            val commandSlot = slot<ProductCommand.IncreaseStocks>()
            every { productService.increaseStocks(capture(commandSlot)) } just runs

            // when
            orderEventListener.onOrderCanceled(event)

            // then
            verify(exactly = 1) { productService.increaseStocks(any()) }

            val capturedCommand = commandSlot.captured
            assertThat(capturedCommand.units).hasSize(2)
            assertThat(capturedCommand.units[0].productId).isEqualTo(1L)
            assertThat(capturedCommand.units[0].amount).isEqualTo(2)
            assertThat(capturedCommand.units[1].productId).isEqualTo(2L)
            assertThat(capturedCommand.units[1].amount).isEqualTo(3)
        }
    }
}
