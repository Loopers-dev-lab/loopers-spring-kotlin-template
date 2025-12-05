package com.loopers.domain.payment

import com.loopers.domain.shared.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class PaymentTest {

    @DisplayName("카드 결제 Payment를 생성할 수 있다")
    @Test
    fun createCardPayment() {
        val orderId = 1L
        val amount = Money.of(10000)
        val transactionKey = "TR-20250104-001"
        val cardType = "SAMSUNG"
        val cardNo = "1234-5678-9012-3456"

        val payment = Payment.createCardPayment(
            orderId = orderId,
            amount = amount,
            transactionKey = transactionKey,
            cardType = cardType,
            cardNo = cardNo
        )

        assertAll(
            { assertThat(payment.orderId).isEqualTo(orderId) },
            { assertThat(payment.amount).isEqualTo(amount) },
            { assertThat(payment.paymentMethod).isEqualTo(PaymentMethod.CARD) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(payment.transactionKey).isEqualTo(transactionKey) },
            { assertThat(payment.cardType).isEqualTo(cardType) },
            { assertThat(payment.cardNumber).isNotNull() }
        )
    }

    @DisplayName("실패한 Payment를 생성할 수 있다")
    @Test
    fun createFailedPayment() {
        val orderId = 1L
        val amount = Money.of(10000)
        val reason = "PG 시스템 일시 불가"

        val payment = Payment.createFailedPayment(
            orderId = orderId,
            amount = amount,
            reason = reason
        )

        assertAll(
            { assertThat(payment.orderId).isEqualTo(orderId) },
            { assertThat(payment.amount).isEqualTo(amount) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
            { assertThat(payment.failureReason).isEqualTo(reason) }
        )
    }

    @DisplayName("PENDING 상태의 Payment를 SUCCESS로 변경할 수 있다")
    @Test
    fun markPaymentAsSuccess() {
        val payment = Payment.createCardPayment(
            orderId = 1L,
            amount = Money.of(10000),
            transactionKey = "TR-001",
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456"
        )

        payment.markAsSuccess()

        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
    }

    @DisplayName("PENDING 상태의 Payment를 FAILED로 변경할 수 있다")
    @Test
    fun markPaymentAsFailed() {
        val payment = Payment.createCardPayment(
            orderId = 1L,
            amount = Money.of(10000),
            transactionKey = "TR-001",
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456"
        )
        val failureReason = "재고 부족"

        payment.markAsFailed(failureReason)

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
            { assertThat(payment.failureReason).isEqualTo(failureReason) }
        )
    }

    @DisplayName("PENDING이 아닌 상태에서 상태 변경 시 예외가 발생한다")
    @Test
    fun failToChangeStatusWhenNotPending() {
        val failedPayment = Payment.createFailedPayment(1L, Money.of(10000), "실패")
        val successPayment = Payment.createCardPayment(1L, Money.of(10000), "TR-001", "SAMSUNG", "1234-5678-9012-3456")
        successPayment.markAsSuccess()

        assertAll(
            {
                val ex = assertThrows<CoreException> { failedPayment.markAsSuccess() }
                assertThat(ex.errorType).isEqualTo(ErrorType.INVALID_PAYMENT_STATUS)
            },
            {
                val ex = assertThrows<CoreException> { successPayment.markAsFailed("실패") }
                assertThat(ex.errorType).isEqualTo(ErrorType.INVALID_PAYMENT_STATUS)
            }
        )
    }
}
