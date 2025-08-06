package com.loopers.domain.payment

import com.loopers.domain.payment.entity.Payment
import com.loopers.domain.payment.entity.Payment.Method.POINT
import com.loopers.domain.payment.entity.Payment.Status.FAILED
import com.loopers.domain.payment.entity.Payment.Status.REQUESTED
import com.loopers.domain.payment.entity.Payment.Status.SUCCESS
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.Test

class PaymentTest {
    @Test
    fun `정상적으로 결제 엔티티를 생성한다`() {
        // when
        val payment = Payment.create(1L, POINT, BigDecimal("1"), REQUESTED)

        // then
        assertThat(payment.orderId).isEqualTo(1L)
        assertThat(payment.paymentMethod).isEqualTo(POINT)
        assertThat(payment.paymentPrice.value).isEqualTo(BigDecimal("1"))
        assertThat(payment.status).isEqualTo(REQUESTED)
    }

    @Test
    fun `결제를 성공 상태로 변경한다`() {
        // given
        val payment = Payment.create(1L, POINT, BigDecimal("1"), REQUESTED)

        // when
        payment.success()

        // then
        assertThat(payment.status).isEqualTo(SUCCESS)
    }

    @Test
    fun `결제를 실패 상태로 변경한다`() {
        // given
        val payment = Payment.create(1L, POINT, BigDecimal("1"), REQUESTED)

        // when
        payment.failure("failReason")

        // then
        assertThat(payment.status).isEqualTo(FAILED)
    }

    @Test
    fun `결제 금액이 음수면 예외가 발생한다`() {
        // expect
        assertThrows<CoreException> {
            Payment.create(1L, POINT, BigDecimal("-1"), REQUESTED)
        }
    }

    @Test
    fun `결제 요청 상태일 때 주문을 성공 처리할 수 있다`() {
        // given
        val payment = Payment.create(1L, POINT, BigDecimal("1"), REQUESTED)

        // when
        payment.success()

        // then
        assertThat(payment.status).isEqualTo(SUCCESS)
    }

    @Test
    fun `결제 요청 상태일 때 주문을 실패 처리할 수 있다`() {
        // given
        val payment = Payment.create(1L, POINT, BigDecimal("1"), REQUESTED)

        // when
        payment.failure("failReason")

        // then
        assertThat(payment.status).isEqualTo(FAILED)
    }

    @Test
    fun `결제 요청 상태가 아니면 주문 성공 처리 시 예외가 발생한다`() {
        // given
        val payment = Payment.create(1L, POINT, BigDecimal("1"), SUCCESS)

        // expect
        assertThrows<CoreException> {
            payment.success()
        }
    }

    @Test
    fun `결제 요청 상태가 아니면 주문 실패 처리 시 예외가 발생한다`() {
        // given
        val payment = Payment.create(1L, POINT, BigDecimal("1"), SUCCESS)

        // expect
        assertThrows<CoreException> {
            payment.failure("failReason")
        }
    }
}
