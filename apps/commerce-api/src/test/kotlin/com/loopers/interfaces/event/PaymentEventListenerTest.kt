package com.loopers.interfaces.event

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.PaymentCreatedEventV1
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.payment.PaymentPaidEventV1
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PgPaymentResult
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.support.values.Money
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PaymentEventListenerTest {
    private lateinit var paymentService: PaymentService
    private lateinit var orderService: OrderService
    private lateinit var pointService: PointService
    private lateinit var couponService: CouponService
    private lateinit var productService: ProductService
    private lateinit var paymentEventListener: PaymentEventListener

    @BeforeEach
    fun setUp() {
        paymentService = mockk()
        orderService = mockk()
        pointService = mockk()
        couponService = mockk()
        productService = mockk()
        paymentEventListener = PaymentEventListener(
            paymentService = paymentService,
            orderService = orderService,
            pointService = pointService,
            couponService = couponService,
            productService = productService,
        )
    }

    @Nested
    @DisplayName("onPaymentCreated")
    inner class OnPaymentCreated {
        @Test
        @DisplayName("paymentService.requestPgPayment(paymentId)를 호출한다")
        fun `calls paymentService requestPgPayment with paymentId`() {
            // given
            val event = PaymentCreatedEventV1(paymentId = 100L)
            every { paymentService.requestPgPayment(100L, any()) } returns mockk<PgPaymentResult>()

            // when
            paymentEventListener.onPaymentCreated(event)

            // then
            verify(exactly = 1) { paymentService.requestPgPayment(100L, any()) }
        }
    }

    @Nested
    @DisplayName("onPaymentPaid")
    inner class OnPaymentPaid {
        @Test
        @DisplayName("orderService.completePayment(orderId)를 호출한다")
        fun `calls orderService completePayment with orderId`() {
            // given
            val event = PaymentPaidEventV1(paymentId = 100L, orderId = 200L)
            every { orderService.completePayment(200L) } returns mockk<Order>()

            // when
            paymentEventListener.onPaymentPaid(event)

            // then
            verify(exactly = 1) { orderService.completePayment(200L) }
        }
    }

    @Nested
    @DisplayName("onPaymentFailed")
    inner class OnPaymentFailed {

        private fun createMockOrderWithItems(): Order {
            val orderItem1 = mockk<OrderItem> {
                every { productId } returns 10L
                every { quantity } returns 2
            }
            val orderItem2 = mockk<OrderItem> {
                every { productId } returns 20L
                every { quantity } returns 3
            }
            return mockk<Order> {
                every { orderItems } returns mutableListOf(orderItem1, orderItem2)
            }
        }

        @Test
        @DisplayName("usedPoint > 0일 때 pointService.restore()를 호출한다")
        fun `calls pointService restore when usedPoint greater than zero`() {
            // given
            val usedPoint = Money.krw(1000L)
            val event = PaymentFailedEventV1(
                paymentId = 100L,
                orderId = 200L,
                userId = 1L,
                usedPoint = usedPoint,
                issuedCouponId = null,
            )
            every { pointService.restore(1L, usedPoint) } returns mockk<PointAccount>()
            every { orderService.cancelOrder(200L) } returns createMockOrderWithItems()
            every { productService.increaseStocks(any()) } just runs

            // when
            paymentEventListener.onPaymentFailed(event)

            // then
            verify(exactly = 1) { pointService.restore(1L, usedPoint) }
        }

        @Test
        @DisplayName("usedPoint가 0일 때 pointService.restore()를 호출하지 않는다")
        fun `does not call pointService restore when usedPoint is zero`() {
            // given
            val event = PaymentFailedEventV1(
                paymentId = 100L,
                orderId = 200L,
                userId = 1L,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
            )
            every { orderService.cancelOrder(200L) } returns createMockOrderWithItems()
            every { productService.increaseStocks(any()) } just runs

            // when
            paymentEventListener.onPaymentFailed(event)

            // then
            verify(exactly = 0) { pointService.restore(any(), any()) }
        }

        @Test
        @DisplayName("issuedCouponId가 있을 때 couponService.cancelCouponUse()를 호출한다")
        fun `calls couponService cancelCouponUse when issuedCouponId is present`() {
            // given
            val event = PaymentFailedEventV1(
                paymentId = 100L,
                orderId = 200L,
                userId = 1L,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = 50L,
            )
            every { couponService.cancelCouponUse(50L) } just runs
            every { orderService.cancelOrder(200L) } returns createMockOrderWithItems()
            every { productService.increaseStocks(any()) } just runs

            // when
            paymentEventListener.onPaymentFailed(event)

            // then
            verify(exactly = 1) { couponService.cancelCouponUse(50L) }
        }

        @Test
        @DisplayName("issuedCouponId가 null일 때 couponService.cancelCouponUse()를 호출하지 않는다")
        fun `does not call couponService cancelCouponUse when issuedCouponId is null`() {
            // given
            val event = PaymentFailedEventV1(
                paymentId = 100L,
                orderId = 200L,
                userId = 1L,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
            )
            every { orderService.cancelOrder(200L) } returns createMockOrderWithItems()
            every { productService.increaseStocks(any()) } just runs

            // when
            paymentEventListener.onPaymentFailed(event)

            // then
            verify(exactly = 0) { couponService.cancelCouponUse(any()) }
        }

        @Test
        @DisplayName("orderService.cancelOrder()를 호출한다")
        fun `calls orderService cancelOrder`() {
            // given
            val event = PaymentFailedEventV1(
                paymentId = 100L,
                orderId = 200L,
                userId = 1L,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
            )
            every { orderService.cancelOrder(200L) } returns createMockOrderWithItems()
            every { productService.increaseStocks(any()) } just runs

            // when
            paymentEventListener.onPaymentFailed(event)

            // then
            verify(exactly = 1) { orderService.cancelOrder(200L) }
        }

        @Test
        @DisplayName("productService.increaseStocks()를 호출하여 재고를 복구한다")
        fun `calls productService increaseStocks to restore stock`() {
            // given
            val event = PaymentFailedEventV1(
                paymentId = 100L,
                orderId = 200L,
                userId = 1L,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
            )
            every { orderService.cancelOrder(200L) } returns createMockOrderWithItems()
            every { productService.increaseStocks(any()) } just runs

            // when
            paymentEventListener.onPaymentFailed(event)

            // then
            verify(exactly = 1) {
                productService.increaseStocks(
                    match { command ->
                        command.units.size == 2 &&
                            command.units.any { it.productId == 10L && it.amount == 2 } &&
                            command.units.any { it.productId == 20L && it.amount == 3 }
                    },
                )
            }
        }
    }
}
