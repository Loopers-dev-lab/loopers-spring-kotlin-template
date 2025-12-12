package com.loopers.domain.payment

import com.loopers.support.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class PaymentPaidEventV1Test {

    @DisplayName("PaymentPaidEventV1 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("DomainEvent 인터페이스를 구현한다")
        @Test
        fun `event implements DomainEvent`() {
            // given
            val paymentId = 1L
            val orderId = 100L

            // when
            val event = PaymentPaidEventV1(
                paymentId = paymentId,
                orderId = orderId,
            )

            // then
            assertThat(event).isInstanceOf(DomainEvent::class.java)
        }

        @DisplayName("event contains paymentId and orderId")
        @Test
        fun `event contains paymentId and orderId`() {
            // given
            val paymentId = 1L
            val orderId = 100L

            // when
            val event = PaymentPaidEventV1(
                paymentId = paymentId,
                orderId = orderId,
            )

            // then
            assertThat(event.paymentId).isEqualTo(paymentId)
            assertThat(event.orderId).isEqualTo(orderId)
        }

        @DisplayName("eventType은 'PaymentPaidEventV1'이다")
        @Test
        fun `eventType is PaymentPaidEventV1`() {
            // given
            val paymentId = 1L
            val orderId = 100L

            // when
            val event = PaymentPaidEventV1(
                paymentId = paymentId,
                orderId = orderId,
            )

            // then
            assertThat(event.eventType).isEqualTo("PaymentPaidEventV1")
        }

        @DisplayName("aggregateType은 'Payment'이다")
        @Test
        fun `aggregateType is Payment`() {
            // given
            val paymentId = 1L
            val orderId = 100L

            // when
            val event = PaymentPaidEventV1(
                paymentId = paymentId,
                orderId = orderId,
            )

            // then
            assertThat(event.aggregateType).isEqualTo("Payment")
        }

        @DisplayName("aggregateId는 paymentId를 문자열로 변환한 값이다")
        @Test
        fun `aggregateId is paymentId as string`() {
            // given
            val paymentId = 456L
            val orderId = 100L

            // when
            val event = PaymentPaidEventV1(
                paymentId = paymentId,
                orderId = orderId,
            )

            // then
            assertThat(event.aggregateId).isEqualTo("456")
        }

        @DisplayName("version은 1이다")
        @Test
        fun `version is 1`() {
            // given
            val paymentId = 1L
            val orderId = 100L

            // when
            val event = PaymentPaidEventV1(
                paymentId = paymentId,
                orderId = orderId,
            )

            // then
            assertThat(event.version).isEqualTo(1)
        }
    }
}
