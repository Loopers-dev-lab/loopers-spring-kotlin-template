package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Order 도메인 테스트")
class OrderTest {

    @Nested
    @DisplayName("주문 생성")
    inner class CreateTest {

        @Test
        @DisplayName("정상적으로 주문이 생성된다")
        fun createOrder() {
            // given
            val totalAmount = 10000L
            val userId = "user123"

            // when
            val order = Order.create(totalAmount, userId)

            // then
            assertSoftly { softly ->
                softly.assertThat(order.totalAmount).isEqualTo(totalAmount)
                softly.assertThat(order.userId).isEqualTo(userId)
                softly.assertThat(order.status).isEqualTo(OrderStatus.PENDING)
            }
        }

        @ParameterizedTest
        @ValueSource(longs = [0L, -1L, -100L, -10000L])
        @DisplayName("주문 총액이 0 이하이면 예외가 발생한다")
        fun createOrderWithInvalidAmount(totalAmount: Long) {
            // given
            val userId = "user123"

            // when & then
            assertThatThrownBy { Order.create(totalAmount, userId) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("주문 총액은 0보다 커야 합니다.")
        }
    }

    @Nested
    @DisplayName("주문 완료")
    inner class CompleteTest {

        @Test
        @DisplayName("PENDING 상태의 주문은 정상적으로 완료된다")
        fun completePendingOrder() {
            // given
            val order = Order.create(10000L, "user123")

            // when
            order.complete()

            // then
            assertSoftly { softly ->
                softly.assertThat(order.status).isEqualTo(OrderStatus.COMPLETED)
            }
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus::class, names = ["COMPLETED", "CANCELLED"])
        @DisplayName("PENDING이 아닌 상태의 주문은 완료할 수 없다")
        fun completeNonPendingOrder(initialStatus: OrderStatus) {
            // given
            val order = Order.create(10000L, "user123")
            when (initialStatus) {
                OrderStatus.COMPLETED -> order.complete()
                OrderStatus.CANCELLED -> order.cancel()
                else -> {}
            }

            // when & then
            assertThatThrownBy { order.complete() }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.ORDER_NOT_COMPLETABLE)
        }
    }

    @Nested
    @DisplayName("주문 취소")
    inner class CancelTest {

        @Test
        @DisplayName("PENDING 상태의 주문은 정상적으로 취소된다")
        fun cancelPendingOrder() {
            // given
            val order = Order.create(10000L, "user123")

            // when
            order.cancel()

            // then
            assertSoftly { softly ->
                softly.assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
            }
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus::class, names = ["COMPLETED", "CANCELLED"])
        @DisplayName("PENDING이 아닌 상태의 주문은 취소할 수 없다")
        fun cancelNonPendingOrder(initialStatus: OrderStatus) {
            // given
            val order = Order.create(10000L, "user123")
            when (initialStatus) {
                OrderStatus.COMPLETED -> order.complete()
                OrderStatus.CANCELLED -> order.cancel()
                else -> {}
            }

            // when & then
            assertThatThrownBy { order.cancel() }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.ORDER_NOT_CANCELLABLE)
        }
    }
}
