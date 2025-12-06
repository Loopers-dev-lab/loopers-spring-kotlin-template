package com.loopers.application.payment

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderService
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.infrastructure.payment.PgRequestNotReachedException
import com.loopers.support.values.Money
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

@DisplayName("PaymentFacade 단위 테스트")
class PaymentFacadeTest {
    private lateinit var paymentService: PaymentService
    private lateinit var orderService: OrderService
    private lateinit var pointService: PointService
    private lateinit var couponService: CouponService
    private lateinit var productService: ProductService

    private lateinit var facade: PaymentFacade

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        paymentService = mockk()
        orderService = mockk()
        pointService = mockk()
        couponService = mockk()
        productService = mockk()

        facade = PaymentFacade(
            paymentService = paymentService,
            orderService = orderService,
            pointService = pointService,
            couponService = couponService,
            productService = productService,
        )
    }

    @Nested
    @DisplayName("findInProgressPayments")
    inner class FindInProgressPayments {

        @Test
        @DisplayName("PaymentService에 위임하여 IN_PROGRESS 결제 목록을 조회한다")
        fun `delegates to PaymentService`() {
            // given
            val threshold = ZonedDateTime.now().minusMinutes(1)
            val payments = listOf(createMockPayment(id = 1L))
            every { paymentService.findInProgressPayments(threshold) } returns payments

            // when
            val result = facade.findInProgressPayments(threshold)

            // then
            verify(exactly = 1) { paymentService.findInProgressPayments(threshold) }
            assert(result == payments)
        }
    }

    @Nested
    @DisplayName("processInProgressPayment - 결과에 따른 처리")
    inner class ProcessInProgressPayment {

        @Test
        @DisplayName("Confirmed(PAID) 결과 시 completePayment를 호출한다")
        fun `calls completePayment when Confirmed with PAID`() {
            // given
            val paymentId = 1L
            val payment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.PAID,
            )

            every { paymentService.processInProgressPayment(paymentId, any()) } returns
                PaymentService.CallbackResult.Confirmed(payment)
            every { orderService.completePayment(200L) } returns mockk()

            // when
            facade.processInProgressPayment(paymentId)

            // then
            verify(exactly = 1) { paymentService.processInProgressPayment(paymentId, any()) }
            verify(exactly = 1) { orderService.completePayment(200L) }
        }

        @Test
        @DisplayName("Confirmed(FAILED) 결과 시 cancelOrder와 리소스 복구를 호출한다")
        fun `calls cancelOrder and recoverResources when Confirmed with FAILED`() {
            // given
            val paymentId = 1L
            val payment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.FAILED,
            )
            val order = createMockOrder(orderId = 200L)

            every { paymentService.processInProgressPayment(paymentId, any()) } returns
                PaymentService.CallbackResult.Confirmed(payment)
            every { orderService.findById(200L) } returns order
            every { productService.increaseStocks(any()) } just Runs
            every { orderService.cancelOrder(200L) } returns mockk()

            // when
            facade.processInProgressPayment(paymentId)

            // then
            verify(exactly = 1) { paymentService.processInProgressPayment(paymentId, any()) }
            verify(exactly = 1) { orderService.cancelOrder(200L) }
        }

        @Test
        @DisplayName("AlreadyProcessed 결과 시 아무 후속 처리도 하지 않는다")
        fun `does nothing when AlreadyProcessed`() {
            // given
            val paymentId = 1L
            val payment = createMockPayment(
                id = paymentId,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.PAID,
            )

            every { paymentService.processInProgressPayment(paymentId, any()) } returns
                PaymentService.CallbackResult.AlreadyProcessed(payment)

            // when
            facade.processInProgressPayment(paymentId)

            // then
            verify(exactly = 1) { paymentService.processInProgressPayment(paymentId, any()) }
            verify(exactly = 0) { orderService.completePayment(any()) }
            verify(exactly = 0) { orderService.cancelOrder(any()) }
        }

        @Test
        @DisplayName("PG 조회 실패 시 예외를 던진다")
        fun `throws exception when PG query fails`() {
            // given
            val paymentId = 1L

            every {
                paymentService.processInProgressPayment(paymentId, any())
            } throws PgRequestNotReachedException("PG 연결 실패")

            // when & then
            org.junit.jupiter.api.assertThrows<PgRequestNotReachedException> {
                facade.processInProgressPayment(paymentId)
            }
        }
    }

    @Nested
    @DisplayName("processCallback - 결과에 따른 처리")
    inner class ProcessCallback {

        @Test
        @DisplayName("Confirmed(PAID) 결과 시 completePayment를 호출한다")
        fun `calls completePayment when Confirmed with PAID`() {
            // given
            val criteria = PaymentCriteria.ProcessCallback(
                orderId = 200L,
                externalPaymentKey = "tx_123",
            )
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.PAID,
            )

            every { paymentService.processCallback(200L, "tx_123", any()) } returns
                PaymentService.CallbackResult.Confirmed(payment)
            every { orderService.completePayment(200L) } returns mockk()

            // when
            facade.processCallback(criteria)

            // then
            verify(exactly = 1) { paymentService.processCallback(200L, "tx_123", any()) }
            verify(exactly = 1) { orderService.completePayment(200L) }
        }

        @Test
        @DisplayName("AlreadyProcessed 결과 시 아무 후속 처리도 하지 않는다")
        fun `does nothing when AlreadyProcessed`() {
            // given
            val criteria = PaymentCriteria.ProcessCallback(
                orderId = 200L,
                externalPaymentKey = "tx_123",
            )
            val payment = createMockPayment(
                id = 1L,
                userId = 100L,
                orderId = 200L,
                status = PaymentStatus.PAID,
            )

            every { paymentService.processCallback(200L, "tx_123", any()) } returns
                PaymentService.CallbackResult.AlreadyProcessed(payment)

            // when
            facade.processCallback(criteria)

            // then
            verify(exactly = 1) { paymentService.processCallback(200L, "tx_123", any()) }
            verify(exactly = 0) { orderService.completePayment(any()) }
            verify(exactly = 0) { orderService.cancelOrder(any()) }
        }
    }

    private fun createMockPayment(
        id: Long = 1L,
        userId: Long = 100L,
        orderId: Long = 200L,
        status: PaymentStatus = PaymentStatus.IN_PROGRESS,
        createdAt: ZonedDateTime = ZonedDateTime.now().minusMinutes(2),
        externalPaymentKey: String? = "tx_mock_$id",
    ): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns id
        every { payment.userId } returns userId
        every { payment.orderId } returns orderId
        every { payment.status } returns status
        every { payment.createdAt } returns createdAt
        every { payment.usedPoint } returns Money.krw(0)
        every { payment.issuedCouponId } returns null
        every { payment.externalPaymentKey } returns externalPaymentKey
        return payment
    }

    private fun createMockOrder(orderId: Long): Order {
        val orderItem = mockk<OrderItem>()
        every { orderItem.productId } returns 1L
        every { orderItem.quantity } returns 1

        val order = mockk<Order>()
        every { order.id } returns orderId
        every { order.orderItems } returns mutableListOf(orderItem)
        return order
    }
}
