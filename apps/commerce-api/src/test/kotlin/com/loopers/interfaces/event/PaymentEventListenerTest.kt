package com.loopers.interfaces.event

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.integration.DataPlatformPublisher
import com.loopers.domain.order.OrderDetail
import com.loopers.domain.order.OrderEvent
import com.loopers.domain.order.OrderService
import com.loopers.domain.outbox.AggregateType
import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxService
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentEvent
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PgService
import com.loopers.domain.product.ProductService
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate

@DisplayName("PaymentEventListener 단위 테스트")
class PaymentEventListenerTest {

    private val pgService: PgService = mockk()
    private val paymentService: PaymentService = mockk()
    private val orderService: OrderService = mockk()
    private val productService: ProductService = mockk()
    private val couponService: CouponService = mockk()
    private val transactionTemplate: TransactionTemplate = mockk()
    private val dataPlatformPublisher: DataPlatformPublisher = mockk()
    private val applicationEventPublisher: ApplicationEventPublisher = mockk()
    private val outboxService: OutboxService = mockk()

    private val paymentEventListener = PaymentEventListener(
        pgService = pgService,
        paymentService = paymentService,
        orderService = orderService,
        productService = productService,
        couponService = couponService,
        transactionTemplate = transactionTemplate,
        dataPlatformPublisher = dataPlatformPublisher,
        applicationEventPublisher = applicationEventPublisher,
        outboxService = outboxService,
    )

    @Nested
    @DisplayName("결제 요청 이벤트 처리 - PG API 요청")
    inner class HandlePaymentRequest {

        @Test
        @DisplayName("PG API를 호출하고 transactionKey를 업데이트한다")
        fun `should request payment to PG and update transaction key`() {
            // given
            val event = PaymentEvent.PaymentRequest(
                paymentId = 1L,
                orderId = "100",
                userId = "user123",
                cardType = CardType.KB,
                cardNo = "1234-5678-9012-3456",
                amount = 50000L,
            )

            val transactionKey = "TXN_KEY_123"

            every {
                pgService.requestPayment(any())
            } returns transactionKey

            every {
                transactionTemplate.execute<Unit>(any())
            } answers {
                val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Unit>>()
                callback.doInTransaction(mockk(relaxed = true))
            }

            justRun { paymentService.updateTransactionKey(any(), any()) }

            // when
            paymentEventListener.handlePaymentRequest(event)

            // then
            verify(exactly = 1) {
                pgService.requestPayment(
                    match {
                        it.userId == "user123" &&
                                it.orderId == "100" &&
                                it.cardType == CardType.KB &&
                                it.cardNo == "1234-5678-9012-3456" &&
                                it.amount == 50000L
                    },
                )
            }
            verify(exactly = 1) { paymentService.updateTransactionKey(1L, transactionKey) }
        }

        @Test
        @DisplayName("PG 요청 실패 시 예외가 전파되지 않는다")
        fun `should not propagate exception when PG request fails`() {
            // given
            val event = PaymentEvent.PaymentRequest(
                paymentId = 1L,
                orderId = "100",
                userId = "user123",
                cardType = CardType.KB,
                cardNo = "1234-5678-9012-3456",
                amount = 50000L,
            )

            every { pgService.requestPayment(any()) } throws RuntimeException("PG 연결 실패")

            // when
            paymentEventListener.handlePaymentRequest(event)

            // then
            verify(exactly = 1) { pgService.requestPayment(any()) }
            verify(exactly = 0) { paymentService.updateTransactionKey(any(), any()) }
        }
    }

    @Nested
    @DisplayName("결제 성공 이벤트 처리 - 주문 완료, 재고 차감")
    inner class HandlePaymentSucceeded {

        @Test
        @DisplayName("주문 완료 처리, 재고 차감, outbox 저장을 수행한다")
        fun `should complete order, deduct stock, and save to outbox`() {
            // given
            val event = PaymentEvent.PaymentSucceeded(
                paymentId = 1L,
                orderId = 100L,
                userId = 1L,
                totalAmount = 50000L,
            )

            val mockOrderDetail = mockk<OrderDetail>()
            every { mockOrderDetail.productId } returns 123L
            every { mockOrderDetail.quantity } returns 2L
            val orderDetails = listOf(mockOrderDetail)

            justRun { orderService.complete(100L) }
            every { orderService.getOrderDetail(100L) } returns orderDetails
            justRun { productService.deductAllStock(orderDetails) }
            justRun {
                outboxService.save(
                    aggregateType = AggregateType.ORDER,
                    aggregateId = any(),
                    eventType = OutboxEvent.OrderCompleted.EVENT_TYPE,
                    payload = any(),
                )
            }

            // when
            paymentEventListener.handlePaymentSucceeded(event)

            // then
            verify(exactly = 1) { orderService.complete(100L) }
            verify(exactly = 1) { orderService.getOrderDetail(100L) }
            verify(exactly = 1) { productService.deductAllStock(orderDetails) }
        }

        @Test
        @DisplayName("재고 차감 실패 시 예외가 전파된다")
        fun `should propagate exception when stock deduction fails`() {
            // given
            val event = PaymentEvent.PaymentSucceeded(
                paymentId = 1L,
                orderId = 100L,
                userId = 1L,
                totalAmount = 50000L,
            )

            val orderDetails = listOf<OrderDetail>(mockk())

            justRun { orderService.complete(100L) }
            every { orderService.getOrderDetail(100L) } returns orderDetails
            every { productService.deductAllStock(orderDetails) } throws RuntimeException("재고 부족")

            // when & then
            org.junit.jupiter.api.assertThrows<RuntimeException> {
                paymentEventListener.handlePaymentSucceeded(event)
            }

            verify(exactly = 1) { orderService.complete(100L) }
            verify(exactly = 1) { productService.deductAllStock(orderDetails) }
            verify(exactly = 0) { applicationEventPublisher.publishEvent(any<OrderEvent.OrderCompleted>()) }
        }
    }

    @Nested
    @DisplayName("결제 실패 이벤트 처리 - 주문 실패, 쿠폰 롤백")
    inner class HandlePaymentFailed {

        @Test
        @DisplayName("주문 실패 처리, 쿠폰 롤백, outbox 저장, 주문 실패 이벤트를 발행한다")
        fun `should fail order, rollback coupon, and publish order failed event`() {
            // given
            val event = PaymentEvent.PaymentFailed(
                paymentId = 1L,
                orderId = 100L,
                userId = 1L,
                couponId = 50L,
                reason = "카드 승인 거부",
            )

            val mockOrderDetail = mockk<OrderDetail>()
            every { mockOrderDetail.productId } returns 123L
            every { mockOrderDetail.quantity } returns 2L
            val orderDetails = listOf(mockOrderDetail)

            every { orderService.getOrderDetail(100L) } returns orderDetails
            justRun { orderService.fail(100L) }
            justRun { couponService.rollback(1L, 50L) }
            justRun {
                outboxService.save(
                    aggregateType = AggregateType.ORDER,
                    aggregateId = any(),
                    eventType = OutboxEvent.OrderCanceled.EVENT_TYPE,
                    payload = any(),
                )
            }

            // when
            paymentEventListener.handlePaymentFailed(event)

            // then
            verify(exactly = 1) { orderService.fail(100L) }
            verify(exactly = 1) { orderService.getOrderDetail(100L) }
            verify(exactly = 1) { couponService.rollback(1L, 50L) }
        }

        @Test
        @DisplayName("쿠폰이 없는 경우 롤백을 스킵한다")
        fun `should skip coupon rollback when couponId is null`() {
            // given
            val event = PaymentEvent.PaymentFailed(
                paymentId = 1L,
                orderId = 100L,
                userId = 1L,
                couponId = null,
                reason = "카드 승인 거부",
            )
            val mockOrderDetail = mockk<OrderDetail>()
            every { mockOrderDetail.productId } returns 123L
            every { mockOrderDetail.quantity } returns 2L
            val orderDetails = listOf(mockOrderDetail)

            every { orderService.getOrderDetail(100L) } returns orderDetails
            justRun { orderService.fail(100L) }
            justRun {
                outboxService.save(
                    aggregateType = AggregateType.ORDER,
                    aggregateId = any(),
                    eventType = OutboxEvent.OrderCanceled.EVENT_TYPE,
                    payload = any(),
                )
            }

            // when
            paymentEventListener.handlePaymentFailed(event)

            // then
            verify(exactly = 1) { orderService.fail(100L) }
            verify(exactly = 1) { orderService.getOrderDetail(100L) }
            verify(exactly = 0) { couponService.rollback(any(), any()) }
        }
    }
}
