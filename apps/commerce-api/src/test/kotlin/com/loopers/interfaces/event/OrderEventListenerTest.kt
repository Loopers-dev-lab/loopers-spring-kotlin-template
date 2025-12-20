package com.loopers.interfaces.event

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.integration.DataPlatformPublisher
import com.loopers.domain.order.OrderEvent
import com.loopers.domain.user.UserActivityEvent
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.orm.ObjectOptimisticLockingFailureException

@DisplayName("OrderEventListener 단위 테스트")
class OrderEventListenerTest {

    private val dataPlatformPublisher: DataPlatformPublisher = mockk()
    private val couponService: CouponService = mockk()
    private val applicationEventPublisher: ApplicationEventPublisher = mockk()

    private val orderEventListener = OrderEventListener(
        dataPlatformPublisher = dataPlatformPublisher,
        couponService = couponService,
        applicationEventPublisher = applicationEventPublisher,
    )

    @Nested
    @DisplayName("주문 생성 이벤트 처리 - 쿠폰 사용")
    inner class HandleOrderCreatedForCoupon {

        @Test
        @DisplayName("할인이 있는 경우 쿠폰을 사용한다")
        fun `should apply coupon when discount amount is greater than zero`() {
            // given
            val event = OrderEvent.OrderCreated(
                orderId = 1L,
                userId = 1L,
                couponId = 100L,
            )

            every { couponService.applyCoupon(any(), any()) } returns Unit

            // when
            orderEventListener.handleOrderCreatedForCoupon(event)

            // then
            verify(exactly = 1) { couponService.applyCoupon(1L, 100L) }
        }

        @Test
        @DisplayName("쿠폰ID가 null이면 쿠폰 사용을 스킵한다")
        fun `should skip coupon application when couponId is null`() {
            // given
            val event = OrderEvent.OrderCreated(
                orderId = 1L,
                userId = 1L,
                couponId = null,
            )

            // when
            orderEventListener.handleOrderCreatedForCoupon(event)

            // then
            verify(exactly = 0) { couponService.applyCoupon(any(), any()) }
        }

        @Test
        @DisplayName("낙관적 락 실패 시 CoreException을 발생시킨다")
        fun `should throw CoreException when optimistic locking fails`() {
            // given
            val event = OrderEvent.OrderCreated(
                orderId = 1L,
                userId = 1L,
                couponId = 100L,
            )

            every { couponService.applyCoupon(any(), any()) } throws ObjectOptimisticLockingFailureException("락 실패", null)

            // when & then
            assertThatThrownBy {
                orderEventListener.handleOrderCreatedForCoupon(event)
            }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.COUPON_ALREADY_USED)
                .hasMessage("이미 사용된 쿠폰입니다")
        }
    }

    @Nested
    @DisplayName("주문 생성 후처리 - 사용자 활동 로깅")
    inner class HandleOrderCreatedForUserActivity {

        @Test
        @DisplayName("사용자 활동 이벤트를 발행한다")
        fun `should send to data platform and publish user activity event`() {
            // given
            val event = OrderEvent.OrderCreated(
                orderId = 1L,
                userId = 1L,
                couponId = 100L,
            )

            justRun { applicationEventPublisher.publishEvent(any<UserActivityEvent.UserActivity>()) }

            // when
            orderEventListener.handleOrderCreatedForUserActivity(event)

            // then
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(
                    match<UserActivityEvent.UserActivity> {
                        it.userId == 1L &&
                                it.activityType == UserActivityEvent.ActivityType.ORDER_PLACED &&
                                it.targetId == 1L
                    },
                )
            }
        }
    }

    @Nested
    @DisplayName("주문 완료 이벤트 처리")
    inner class HandleOrderCompleted {

        @Test
        @DisplayName("데이터 플랫폼으로 주문 완료 정보를 전송하고 사용자 활동 이벤트를 발행한다")
        fun `should send order completed to data platform and publish user activity event`() {
            // given
            val event = OrderEvent.OrderCompleted(
                orderId = 1L,
                userId = 1L,
                totalAmount = 50000L,
                items = emptyList(),
            )

            justRun { dataPlatformPublisher.send(event) }
            justRun { applicationEventPublisher.publishEvent(any<UserActivityEvent.UserActivity>()) }

            // when
            orderEventListener.handleOrderCompleted(event)

            // then
            verify(exactly = 1) { dataPlatformPublisher.send(event) }
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(
                    match<UserActivityEvent.UserActivity> {
                        it.userId == 1L &&
                                it.activityType == UserActivityEvent.ActivityType.ORDER_COMPLETED &&
                                it.targetId == 1L
                    },
                )
            }
        }

        @Test
        @DisplayName("데이터 플랫폼 전송 실패 시에도 예외가 전파되지 않는다")
        fun `should not propagate exception when data platform send fails`() {
            // given
            val event = OrderEvent.OrderCompleted(
                orderId = 1L,
                userId = 1L,
                totalAmount = 50000L,
                items = emptyList(),
            )

            every { dataPlatformPublisher.send(event) } throws RuntimeException("전송 실패")
            justRun { applicationEventPublisher.publishEvent(any<UserActivityEvent.UserActivity>()) }

            // when
            orderEventListener.handleOrderCompleted(event)

            // then
            verify(exactly = 1) { dataPlatformPublisher.send(event) }
            verify(exactly = 1) { applicationEventPublisher.publishEvent(any<UserActivityEvent.UserActivity>()) }
        }
    }

    @Nested
    @DisplayName("주문 실패 이벤트 처리")
    inner class HandleOrderFailed {

        @Test
        @DisplayName("데이터 플랫폼으로 주문 실패 정보를 전송하고 사용자 활동 이벤트를 발행한다")
        fun `should send order failed to data platform and publish user activity event`() {
            // given
            val event = OrderEvent.OrderFailed(
                orderId = 1L,
                userId = 1L,
                reason = "결제 실패",
            )

            justRun { dataPlatformPublisher.send(event) }
            justRun { applicationEventPublisher.publishEvent(any<UserActivityEvent.UserActivity>()) }

            // when
            orderEventListener.handleOrderFailed(event)

            // then
            verify(exactly = 1) { dataPlatformPublisher.send(event) }
            verify(exactly = 1) {
                applicationEventPublisher.publishEvent(
                    match<UserActivityEvent.UserActivity> {
                        it.userId == 1L &&
                                it.activityType == UserActivityEvent.ActivityType.ORDER_FAILED &&
                                it.targetId == 1L &&
                                it.metadata["reason"] == "결제 실패"
                    },
                )
            }
        }
    }
}
