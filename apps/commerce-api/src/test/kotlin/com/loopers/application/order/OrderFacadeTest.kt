package com.loopers.application.order

import com.loopers.application.payment.PaymentFacade
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderQueryService
import com.loopers.domain.order.OrderService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.domain.product.ProductQueryService
import com.loopers.domain.product.StockService
import com.loopers.fixtures.createTestOrder
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

class OrderFacadeTest {
    private val orderService: OrderService = mockk()
    private val orderQueryService: OrderQueryService = mockk()
    private val productQueryService: ProductQueryService = mockk(relaxed = true)
    private val stockService: StockService = mockk(relaxed = true)
    private val pointService: PointService = mockk(relaxed = true)
    private val couponService: CouponService = mockk(relaxed = true)
    private val paymentFacade: PaymentFacade = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)

    private val orderFacade = OrderFacade(
        orderService,
        orderQueryService,
        productQueryService,
        stockService,
        pointService,
        couponService,
        paymentFacade,
        eventPublisher,
    )

    @Test
    fun `주문을 생성할 수 있다`() {
        // given
        val userId = 1L
        val request = OrderCreateRequest(
            items = listOf(
                OrderItemRequest(productId = 100L, quantity = 2),
            ),
        )
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
        val order = createTestOrder(id = 1L, userId = userId, items = items)

        every { orderService.createOrder(userId, any(), any()) } returns order

        // when
        val result = orderFacade.createOrder(userId, request)

        // then
        assertThat(result.orderId).isEqualTo(1L)
        assertThat(result.userId).isEqualTo(userId)
        verify { orderService.createOrder(userId, any(), any()) }
    }

    @Test
    fun `사용자의 주문 목록을 조회할 수 있다`() {
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
                quantity = 2,
                priceAtOrder = Price(BigDecimal("100000"), Currency.KRW),
            ),
        )
        val order = createTestOrder(id = 1L, userId = userId, items = items)

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
        val order = createTestOrder(id = orderId, userId = userId, items = items)

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
