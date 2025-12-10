package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.support.values.Money
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.Test

class PgClientResultDecoratorTest {

    private val delegate: PgClient = mockk()
    private val decorator = PgClientResultDecorator(delegate)

    @DisplayName("requestPayment 테스트")
    @Nested
    inner class RequestPayment {

        @DisplayName("0원 결제 요청 시 delegate 호출 없이 NotRequired를 반환한다")
        @Test
        fun `returns NotRequired without calling delegate when amount is zero`() {
            // given
            val request = createPaymentRequest(amount = Money.ZERO_KRW)

            // when
            val result = decorator.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.NotRequired)
            verify(exactly = 0) { delegate.requestPayment(any()) }
        }

        @DisplayName("delegate 호출 성공 시 Accepted를 반환한다")
        @Test
        fun `returns Accepted when delegate call succeeds`() {
            // given
            val request = createPaymentRequest(amount = Money.krw(10000))
            val transactionKey = "tx_12345"
            every { delegate.requestPayment(request) } returns PgPaymentCreateResult.Accepted(transactionKey)

            // when
            val result = decorator.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.Accepted(transactionKey))
            verify(exactly = 1) { delegate.requestPayment(request) }
        }

        @DisplayName("PgResponseUncertainException 발생 시 Uncertain을 반환한다")
        @Test
        fun `returns Uncertain when PgResponseUncertainException is thrown`() {
            // given
            val request = createPaymentRequest(amount = Money.krw(10000))
            every { delegate.requestPayment(request) } throws PgResponseUncertainException("응답 불확실")

            // when
            val result = decorator.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.Uncertain)
        }

        @DisplayName("PgRequestNotReachedException 발생 시 NotReached를 반환한다")
        @Test
        fun `returns NotReached when PgRequestNotReachedException is thrown`() {
            // given
            val request = createPaymentRequest(amount = Money.krw(10000))
            every { delegate.requestPayment(request) } throws PgRequestNotReachedException("연결 실패")

            // when
            val result = decorator.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.NotReached)
        }

        @DisplayName("CallNotPermittedException 발생 시 NotReached를 반환한다")
        @Test
        fun `returns NotReached when CallNotPermittedException is thrown`() {
            // given
            val request = createPaymentRequest(amount = Money.krw(10000))
            every { delegate.requestPayment(request) } throws mockk<CallNotPermittedException>()

            // when
            val result = decorator.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.NotReached)
        }
    }

    @DisplayName("findTransaction 테스트")
    @Nested
    inner class FindTransaction {

        @DisplayName("delegate로 위임하여 트랜잭션을 조회한다")
        @Test
        fun `delegates to delegate to find transaction`() {
            // given
            val transactionKey = "tx_12345"
            val expectedTransaction = PgTransaction(
                transactionKey = transactionKey,
                paymentId = 1L,
                status = PgTransactionStatus.SUCCESS,
                failureReason = null,
            )
            every { delegate.findTransaction(transactionKey) } returns expectedTransaction

            // when
            val result = decorator.findTransaction(transactionKey)

            // then
            assertThat(result).isEqualTo(expectedTransaction)
            verify(exactly = 1) { delegate.findTransaction(transactionKey) }
        }
    }

    @DisplayName("findTransactionsByPaymentId 테스트")
    @Nested
    inner class FindTransactionsByPaymentId {

        @DisplayName("delegate로 위임하여 트랜잭션 목록을 조회한다")
        @Test
        fun `delegates to delegate to find transactions by payment id`() {
            // given
            val paymentId = 1L
            val expectedTransactions = listOf(
                PgTransaction(
                    transactionKey = "tx_12345",
                    paymentId = paymentId,
                    status = PgTransactionStatus.SUCCESS,
                    failureReason = null,
                ),
                PgTransaction(
                    transactionKey = "tx_67890",
                    paymentId = paymentId,
                    status = PgTransactionStatus.FAILED,
                    failureReason = "잔액 부족",
                ),
            )
            every { delegate.findTransactionsByPaymentId(paymentId) } returns expectedTransactions

            // when
            val result = decorator.findTransactionsByPaymentId(paymentId)

            // then
            assertThat(result).isEqualTo(expectedTransactions)
            verify(exactly = 1) { delegate.findTransactionsByPaymentId(paymentId) }
        }
    }

    private fun createPaymentRequest(
        paymentId: Long = 1L,
        amount: Money = Money.krw(10000),
    ): PgPaymentRequest {
        return PgPaymentRequest(
            paymentId = paymentId,
            amount = amount,
            cardInfo = CardInfo(
                cardType = CardType.SAMSUNG,
                cardNo = "1234-5678-9012-3456",
            ),
        )
    }
}
