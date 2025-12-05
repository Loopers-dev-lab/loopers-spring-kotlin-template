package com.loopers.domain.payment

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PaymentTest {

    @Test
    fun `카드 결제 Payment 생성 시 PENDING 상태로 생성된다`() {
        // given
        val orderId = 1L
        val amount = Money.of(10000)
        val transactionKey = "TR-20250104-001"
        val cardType = "SAMSUNG"
        val cardNo = "1234-5678-9012-3456"

        // when
        val payment = Payment.createCardPayment(
            orderId = orderId,
            amount = amount,
            transactionKey = transactionKey,
            cardType = cardType,
            cardNo = cardNo
        )

        // then
        assertThat(payment.orderId).isEqualTo(orderId)
        assertThat(payment.amount).isEqualTo(amount)
        assertThat(payment.paymentMethod).isEqualTo(PaymentMethod.CARD)
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.transactionKey).isEqualTo(transactionKey)
        assertThat(payment.cardType).isEqualTo(cardType)
        assertThat(payment.cardNo).isEqualTo(cardNo)
    }

    @Test
    fun `실패한 Payment 생성 시 FAILED 상태로 생성된다`() {
        // given
        val orderId = 1L
        val amount = Money.of(10000)
        val reason = "PG 시스템 일시 불가"

        // when
        val payment = Payment.createFailedPayment(
            orderId = orderId,
            amount = amount,
            reason = reason
        )

        // then
        assertThat(payment.orderId).isEqualTo(orderId)
        assertThat(payment.amount).isEqualTo(amount)
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(payment.failureReason).isEqualTo(reason)
    }

    @Test
    fun `PENDING 상태의 Payment를 SUCCESS로 변경할 수 있다`() {
        // given
        val payment = Payment.createCardPayment(
            orderId = 1L,
            amount = Money.of(10000),
            transactionKey = "TR-001",
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456"
        )

        // when
        payment.markAsSuccess()

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
    }

    @Test
    fun `PENDING이 아닌 Payment를 SUCCESS로 변경하면 예외가 발생한다`() {
        // given
        val payment = Payment.createFailedPayment(
            orderId = 1L,
            amount = Money.of(10000),
            reason = "테스트 실패"
        )

        // when & then
        assertThatThrownBy { payment.markAsSuccess() }
            .isInstanceOf(CoreException::class.java)
    }

    @Test
    fun `PENDING 상태의 Payment를 FAILED로 변경할 수 있다`() {
        // given
        val payment = Payment.createCardPayment(
            orderId = 1L,
            amount = Money.of(10000),
            transactionKey = "TR-001",
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456"
        )
        val failureReason = "재고 부족"

        // when
        payment.markAsFailed(failureReason)

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(payment.failureReason).isEqualTo(failureReason)
    }

    @Test
    fun `PENDING이 아닌 Payment를 FAILED로 변경하면 예외가 발생한다`() {
        // given
        val payment = Payment.createCardPayment(
            orderId = 1L,
            amount = Money.of(10000),
            transactionKey = "TR-001",
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456"
        )
        payment.markAsSuccess()

        // when & then
        assertThatThrownBy { payment.markAsFailed("이미 성공함") }
            .isInstanceOf(CoreException::class.java)
    }
}
