package com.loopers.domain.payment

import com.loopers.support.event.DomainEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class PaymentCreatedEventV1Test {

    @DisplayName("PaymentCreatedEventV1 생성 테스트")
    @Nested
    inner class Create {

        @DisplayName("DomainEvent 인터페이스를 구현한다")
        @Test
        fun `event implements DomainEvent`() {
            // given
            val paymentId = 1L

            // when
            val event = PaymentCreatedEventV1(paymentId = paymentId)

            // then
            assertThat(event).isInstanceOf(DomainEvent::class.java)
        }

        @DisplayName("eventType은 'PaymentCreatedEventV1'이다")
        @Test
        fun `eventType is PaymentCreatedEventV1`() {
            // given
            val paymentId = 1L

            // when
            val event = PaymentCreatedEventV1(paymentId = paymentId)

            // then
            assertThat(event.eventType).isEqualTo("PaymentCreatedEventV1")
        }

        @DisplayName("aggregateType은 'Payment'이다")
        @Test
        fun `aggregateType is Payment`() {
            // given
            val paymentId = 1L

            // when
            val event = PaymentCreatedEventV1(paymentId = paymentId)

            // then
            assertThat(event.aggregateType).isEqualTo("Payment")
        }

        @DisplayName("aggregateId는 paymentId를 문자열로 변환한 값이다")
        @Test
        fun `aggregateId is paymentId as string`() {
            // given
            val paymentId = 123L

            // when
            val event = PaymentCreatedEventV1(paymentId = paymentId)

            // then
            assertThat(event.aggregateId).isEqualTo("123")
        }

        @DisplayName("version은 1이다")
        @Test
        fun `version is 1`() {
            // given
            val paymentId = 1L

            // when
            val event = PaymentCreatedEventV1(paymentId = paymentId)

            // then
            assertThat(event.version).isEqualTo(1)
        }
    }
}
