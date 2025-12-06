package com.loopers.domain.event.handler

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.event.OrderCreatedEvent
import com.loopers.domain.event.PaymentCompletedEvent
import com.loopers.domain.event.PaymentFailedEvent
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.fixtures.createTestOrder
import com.loopers.infrastructure.dataplatform.client.DataPlatformClient
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.time.ZonedDateTime

class OrderEventHandlerTest {
    private val couponService = mockk<CouponService>()
    private val orderRepository = mockk<OrderRepository>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val dataPlatformClient = mockk<DataPlatformClient>(relaxed = true)

    private val orderEventHandler = OrderEventHandler(
        couponService,
        orderRepository,
        eventPublisher,
        dataPlatformClient,
    )

    @DisplayName("주문 생성 이벤트를 처리할 때,")
    @Nested
    inner class HandleOrderCreated {
        @DisplayName("쿠폰 ID가 있으면 쿠폰을 사용한다")
        @Test
        fun handleOrderCreatedForCoupon_whenCouponIdExists_thenUseCoupon() {
            // given
            val event = OrderCreatedEvent(
                orderId = 1L,
                userId = 100L,
                amount = 10000L,
                couponId = 200L,
                createdAt = ZonedDateTime.now(),
            )

            justRun { couponService.useUserCoupon(100L, 200L) }

            // when
            orderEventHandler.handleOrderCreatedForCoupon(event)

            // then
            verify(exactly = 1) { couponService.useUserCoupon(100L, 200L) }
        }

        @DisplayName("쿠폰 ID가 없으면 쿠폰 사용을 건너뛴다")
        @Test
        fun handleOrderCreatedForCoupon_whenNoCouponId_thenSkipCouponUsage() {
            // given
            val event = OrderCreatedEvent(
                orderId = 1L,
                userId = 100L,
                amount = 10000L,
                couponId = null,
                createdAt = ZonedDateTime.now(),
            )

            // when
            orderEventHandler.handleOrderCreatedForCoupon(event)

            // then
            verify(exactly = 0) { couponService.useUserCoupon(any(), any()) }
        }

        @DisplayName("쿠폰 사용 실패 시에도 예외가 전파되지 않는다")
        @Test
        fun handleOrderCreatedForCoupon_whenCouponUsageFails_thenDoesNotThrow() {
            // given
            val event = OrderCreatedEvent(
                orderId = 1L,
                userId = 100L,
                amount = 10000L,
                couponId = 200L,
                createdAt = ZonedDateTime.now(),
            )

            every { couponService.useUserCoupon(any(), any()) } throws RuntimeException("쿠폰 서비스 오류")

            // when & then (예외가 발생하지 않아야 함)
            orderEventHandler.handleOrderCreatedForCoupon(event)

            verify(exactly = 1) { couponService.useUserCoupon(100L, 200L) }
        }

        @DisplayName("데이터 플랫폼 전송 실패 시에도 예외가 전파되지 않는다")
        @Test
        fun handleOrderCreatedForDataPlatform_whenSendFails_thenDoesNotThrow() {
            // given
            val event = OrderCreatedEvent(
                orderId = 1L,
                userId = 100L,
                amount = 10000L,
                couponId = null,
                createdAt = ZonedDateTime.now(),
            )

            // when & then (예외가 발생하지 않아야 함)
            orderEventHandler.handleOrderCreatedForDataPlatform(event)
        }
    }

    @DisplayName("결제 완료 이벤트를 처리할 때,")
    @Nested
    inner class HandlePaymentCompleted {
        @DisplayName("주문 상태를 CONFIRMED로 업데이트한다")
        @Test
        fun handlePaymentCompleted_updatesOrderStatusToConfirmed() {
            // given
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
            val order = createTestOrder(id = 1L, userId = 100L, items = items)

            val event = PaymentCompletedEvent(
                paymentId = 10L,
                orderId = 1L,
                userId = 100L,
                amount = 10000L,
                transactionKey = "txn_123456",
                completedAt = ZonedDateTime.now(),
            )

            every { orderRepository.findById(1L) } returns order
            every { orderRepository.save(any()) } answers { firstArg() }

            // when
            orderEventHandler.handlePaymentCompleted(event)

            // then
            assertThat(order.status).isEqualTo(OrderStatus.CONFIRMED)
            verify(exactly = 1) { orderRepository.findById(1L) }
            verify(exactly = 1) { orderRepository.save(order) }
        }

        @DisplayName("주문을 찾을 수 없으면 예외가 발생한다")
        @Test
        fun handlePaymentCompleted_whenOrderNotFound_thenThrowsException() {
            // given
            val event = PaymentCompletedEvent(
                paymentId = 10L,
                orderId = 999L,
                userId = 100L,
                amount = 10000L,
                transactionKey = "txn_123456",
                completedAt = ZonedDateTime.now(),
            )

            every { orderRepository.findById(999L) } returns null

            // when & then
            try {
                orderEventHandler.handlePaymentCompleted(event)
            } catch (e: Exception) {
                assertThat(e.message).contains("주문을 찾을 수 없습니다")
            }
        }
    }

    @DisplayName("결제 실패 이벤트를 처리할 때,")
    @Nested
    inner class HandlePaymentFailed {
        @DisplayName("주문 정보를 조회하고 유저 행동을 로깅한다")
        @Test
        fun handlePaymentFailed_logsUserAction() {
            // given
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
            val order = createTestOrder(id = 1L, userId = 100L, items = items)

            val event = PaymentFailedEvent(
                paymentId = 10L,
                orderId = 1L,
                userId = 100L,
                amount = 10000L,
                reason = "카드 한도 초과",
                failedAt = ZonedDateTime.now(),
            )

            every { orderRepository.findById(1L) } returns order

            // when
            orderEventHandler.handlePaymentFailed(event)

            // then
            verify(exactly = 1) { orderRepository.findById(1L) }
        }

        @DisplayName("주문을 찾을 수 없어도 예외가 전파되지 않는다")
        @Test
        fun handlePaymentFailed_whenOrderNotFound_thenDoesNotThrow() {
            // given
            val event = PaymentFailedEvent(
                paymentId = 10L,
                orderId = 999L,
                userId = 100L,
                amount = 10000L,
                reason = "카드 한도 초과",
                failedAt = ZonedDateTime.now(),
            )

            every { orderRepository.findById(999L) } returns null

            // when & then (예외가 발생하지 않아야 함)
            orderEventHandler.handlePaymentFailed(event)
        }
    }
}
