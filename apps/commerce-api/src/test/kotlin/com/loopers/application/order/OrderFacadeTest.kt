package com.loopers.application.order

import com.loopers.domain.member.MemberService
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItemCommand
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.shared.Money
import com.loopers.interfaces.api.order.OrderV1Dto.CreateOrderRequest as ApiCreateOrderRequest
import com.loopers.interfaces.api.order.OrderV1Dto.OrderItemRequest as ApiOrderItemRequest
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OrderFacadeTest {

    private lateinit var orderFacade: OrderFacade
    private lateinit var orderService: OrderService
    private lateinit var paymentService: PaymentService
    private lateinit var memberService: MemberService

    @BeforeEach
    fun setUp() {
        orderService = mockk()
        paymentService = mockk()
        memberService = mockk()
        orderFacade = OrderFacade(orderService, paymentService, memberService)
    }

    @DisplayName("포인트 전액 결제 시 즉시 주문이 완료된다")
    @Test
    fun completeOrderImmediatelyWithFullPointPayment() {
        // given
        val memberId = "testuser01"
        val request = ApiCreateOrderRequest(
            items = listOf(ApiOrderItemRequest(productId = 1L, quantity = 1)),
            usePoint = 10000L, // 전액 포인트
            cardType = null,
            cardNo = null,
            couponId = null
        )

        val order = mockk<Order>(relaxed = true)
        every { order.id } returns 1L
        every { order.memberId } returns "testuser01"
        every { order.finalAmount } returns Money.of(10000L)
        every { order.status } returns OrderStatus.PENDING
        every { order.totalAmount } returns Money.of(10000L)
        every { order.discountAmount } returns Money.zero()
        every { order.items } returns emptyList()
        every { order.createdAt } returns java.time.ZonedDateTime.now()

        every { orderService.createOrderWithCalculation(any(), any(), any(), 10000L) } returns order
        every { orderService.completeOrderWithPayment(1L) } just Runs

        // when
        val result = orderFacade.createOrder(memberId, request)

        // then
        assertThat(result.id).isEqualTo(1L)
        assertThat(result.status).isEqualTo(OrderStatus.COMPLETED)
        verify(exactly = 1) { orderService.completeOrderWithPayment(1L) }
        verify(exactly = 0) { paymentService.requestCardPayment(any(), any(), any(), any(), any()) }
    }

    @DisplayName("카드 결제 시 PaymentService를 호출한다")
    @Test
    fun requestCardPaymentWhenAmountRemains() {
        // given
        val memberId = "testuser01"
        val request = ApiCreateOrderRequest(
            items = listOf(ApiOrderItemRequest(productId = 1L, quantity = 1)),
            usePoint = 3000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
            couponId = null
        )

        val order = mockk<Order>(relaxed = true)
        every { order.id } returns 1L
        every { order.finalAmount } returns Money.of(10000L)
        every { order.status } returns OrderStatus.PENDING
        every { order.totalAmount } returns Money.of(10000L)
        every { order.discountAmount } returns Money.zero()

        val payment = mockk<Payment>(relaxed = true)

        every { orderService.createOrderWithCalculation(any(), any(), any(), 3000L) } returns order
        every { paymentService.requestCardPayment(any(), any(), any(), any(), any()) } returns payment

        // when
        val result = orderFacade.createOrder(memberId, request)

        // then
        assertThat(result.id).isEqualTo(1L)
        verify(exactly = 1) { paymentService.requestCardPayment(order, memberId, "SAMSUNG", "1234-5678-9012-3456", 7000L) }
        verify(exactly = 0) { orderService.completeOrderWithPayment(any()) }
    }

    @DisplayName("PG 실패 시 포인트가 롤백되지만 주문은 PENDING으로 유지된다")
    @Test
    fun rollbackPointButKeepOrderPendingWhenPgFails() {
        // given
        val memberId = "testuser01"
        val usePoint = 3000L
        val request = ApiCreateOrderRequest(
            items = listOf(ApiOrderItemRequest(productId = 1L, quantity = 1)),
            usePoint = usePoint,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
            couponId = null
        )

        val order = mockk<Order>(relaxed = true)
        every { order.id } returns 1L
        every { order.finalAmount } returns Money.of(10000L)
        every { order.status } returns OrderStatus.PENDING
        every { order.totalAmount } returns Money.of(10000L)
        every { order.discountAmount } returns Money.zero()

        every { orderService.createOrderWithCalculation(any(), any(), any(), usePoint) } returns order
        every { paymentService.requestCardPayment(any(), any(), any(), any(), any()) } throws CoreException(
            ErrorType.PAYMENT_UNAVAILABLE,
            "PG 서버 장애"
        )
        every { memberService.rollbackPoint(memberId, usePoint) } just Runs

        // when
        val result = orderFacade.createOrder(memberId, request)

        // then: 주문은 PENDING으로 생성됨
        assertThat(result.id).isEqualTo(1L)
        assertThat(result.status).isEqualTo(OrderStatus.PENDING)

        // 포인트 롤백 확인
        verify(exactly = 1) { memberService.rollbackPoint(memberId, usePoint) }
    }
}
