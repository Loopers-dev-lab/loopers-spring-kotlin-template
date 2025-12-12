package com.loopers.domain.payment

import com.loopers.support.event.DomainEvent
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class PaymentFailedEventV1Test {

    @DisplayName("PaymentFailedEventV1 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("DomainEvent 인터페이스를 구현한다")
        @Test
        fun `event implements DomainEvent`() {
            // given
            val paymentId = 1L
            val orderId = 100L
            val userId = 10L
            val usedPoint = Money.krw(1000)
            val issuedCouponId = 50L

            // when
            val event = PaymentFailedEventV1(
                paymentId = paymentId,
                orderId = orderId,
                userId = userId,
                usedPoint = usedPoint,
                issuedCouponId = issuedCouponId,
            )

            // then
            assertThat(event).isInstanceOf(DomainEvent::class.java)
        }

        @DisplayName("event contains paymentId, orderId, userId, usedPoint, issuedCouponId")
        @Test
        fun `event contains paymentId, orderId, userId, usedPoint, issuedCouponId`() {
            // given
            val paymentId = 1L
            val orderId = 100L
            val userId = 10L
            val usedPoint = Money.krw(1000)
            val issuedCouponId = 50L

            // when
            val event = PaymentFailedEventV1(
                paymentId = paymentId,
                orderId = orderId,
                userId = userId,
                usedPoint = usedPoint,
                issuedCouponId = issuedCouponId,
            )

            // then
            assertThat(event.paymentId).isEqualTo(paymentId)
            assertThat(event.orderId).isEqualTo(orderId)
            assertThat(event.userId).isEqualTo(userId)
            assertThat(event.usedPoint).isEqualTo(usedPoint)
            assertThat(event.issuedCouponId).isEqualTo(issuedCouponId)
        }

        @DisplayName("issuedCouponId는 nullable이다")
        @Test
        fun `issuedCouponId is nullable`() {
            // given
            val paymentId = 1L
            val orderId = 100L
            val userId = 10L
            val usedPoint = Money.krw(1000)

            // when
            val event = PaymentFailedEventV1(
                paymentId = paymentId,
                orderId = orderId,
                userId = userId,
                usedPoint = usedPoint,
                issuedCouponId = null,
            )

            // then
            assertThat(event.issuedCouponId).isNull()
        }

        @DisplayName("eventType은 'PaymentFailedEventV1'이다")
        @Test
        fun `eventType is PaymentFailedEventV1`() {
            // given
            val paymentId = 1L
            val orderId = 100L
            val userId = 10L
            val usedPoint = Money.krw(1000)

            // when
            val event = PaymentFailedEventV1(
                paymentId = paymentId,
                orderId = orderId,
                userId = userId,
                usedPoint = usedPoint,
                issuedCouponId = null,
            )

            // then
            assertThat(event.eventType).isEqualTo("PaymentFailedEventV1")
        }

        @DisplayName("aggregateType은 'Payment'이다")
        @Test
        fun `aggregateType is Payment`() {
            // given
            val paymentId = 1L
            val orderId = 100L
            val userId = 10L
            val usedPoint = Money.krw(1000)

            // when
            val event = PaymentFailedEventV1(
                paymentId = paymentId,
                orderId = orderId,
                userId = userId,
                usedPoint = usedPoint,
                issuedCouponId = null,
            )

            // then
            assertThat(event.aggregateType).isEqualTo("Payment")
        }

        @DisplayName("aggregateId는 paymentId를 문자열로 변환한 값이다")
        @Test
        fun `aggregateId is paymentId as string`() {
            // given
            val paymentId = 789L
            val orderId = 100L
            val userId = 10L
            val usedPoint = Money.krw(1000)

            // when
            val event = PaymentFailedEventV1(
                paymentId = paymentId,
                orderId = orderId,
                userId = userId,
                usedPoint = usedPoint,
                issuedCouponId = null,
            )

            // then
            assertThat(event.aggregateId).isEqualTo("789")
        }

        @DisplayName("version은 1이다")
        @Test
        fun `version is 1`() {
            // given
            val paymentId = 1L
            val orderId = 100L
            val userId = 10L
            val usedPoint = Money.krw(1000)

            // when
            val event = PaymentFailedEventV1(
                paymentId = paymentId,
                orderId = orderId,
                userId = userId,
                usedPoint = usedPoint,
                issuedCouponId = null,
            )

            // then
            assertThat(event.version).isEqualTo(1)
        }
    }
}
