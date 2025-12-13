package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PaymentTest {
    @Test
    fun `결제를 생성할 수 있다`() {
        // given & when
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )

        // then
        assertThat(payment.userId).isEqualTo(1L)
        assertThat(payment.orderId).isEqualTo(100L)
        assertThat(payment.paymentMethod).isEqualTo(PaymentMethod.CARD)
        assertThat(payment.amount).isEqualTo(10000L)
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.transactionKey).isNull()
        assertThat(payment.failureReason).isNull()
    }

    @Test
    fun `거래 키를 업데이트할 수 있다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )

        // when
        payment.updateTransactionKey("txn_123456")

        // then
        assertThat(payment.transactionKey).isEqualTo("txn_123456")
    }

    @Test
    fun `이미 거래 키가 설정된 경우 예외가 발생한다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )
        payment.updateTransactionKey("txn_123456")

        // when & then
        assertThatThrownBy {
            payment.updateTransactionKey("txn_789012")
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("이미 거래 키가 설정되었습니다")
    }

    @Test
    fun `결제를 완료할 수 있다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )

        // when
        payment.complete()

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETED)
        assertThat(payment.isCompleted()).isTrue()
    }

    @Test
    fun `결제 완료는 멱등성을 가진다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )
        payment.complete()

        // when
        payment.complete() // 두 번째 호출

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETED)
    }

    @Test
    fun `실패한 결제는 완료할 수 없다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )
        payment.fail("카드 한도 초과")

        // when & then
        assertThatThrownBy {
            payment.complete()
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("실패한 결제는 완료할 수 없습니다")
    }

    @Test
    fun `결제를 실패 처리할 수 있다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )

        // when
        payment.fail("카드 한도 초과")

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(payment.failureReason).isEqualTo("카드 한도 초과")
        assertThat(payment.isFailed()).isTrue()
    }

    @Test
    fun `결제 실패는 멱등성을 가진다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )
        payment.fail("카드 한도 초과")

        // when
        payment.fail("다른 실패 사유") // 두 번째 호출

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
    }

    @Test
    fun `완료된 결제는 실패 처리할 수 없다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )
        payment.complete()

        // when & then
        assertThatThrownBy {
            payment.fail("시스템 오류")
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("이미 완료된 결제는 실패 처리할 수 없습니다")
    }

    @Test
    fun `결제를 타임아웃 처리할 수 있다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )

        // when
        payment.timeout()

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.TIMEOUT)
        assertThat(payment.failureReason).isEqualTo("결제 시간이 초과되었습니다.")
        assertThat(payment.isFailed()).isTrue()
    }

    @Test
    fun `이미 처리된 결제는 타임아웃 처리되지 않는다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )
        payment.complete()

        // when
        payment.timeout()

        // then
        assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETED) // 변경되지 않음
    }

    @Test
    fun `결제 상태를 확인할 수 있다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )

        // when & then - PENDING 상태
        assertThat(payment.isPending()).isTrue()
        assertThat(payment.isCompleted()).isFalse()
        assertThat(payment.isFailed()).isFalse()

        // when - 완료 처리
        payment.complete()

        // then - COMPLETED 상태
        assertThat(payment.isPending()).isFalse()
        assertThat(payment.isCompleted()).isTrue()
        assertThat(payment.isFailed()).isFalse()
    }

    @Test
    fun `타임아웃은 실패로 간주된다`() {
        // given
        val payment = Payment(
            userId = 1L,
            orderId = 100L,
            paymentMethod = PaymentMethod.CARD,
            amount = 10000L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-9012-3456",
        )

        // when
        payment.timeout()

        // then
        assertThat(payment.isFailed()).isTrue()
        assertThat(payment.status).isEqualTo(PaymentStatus.TIMEOUT)
    }
}
