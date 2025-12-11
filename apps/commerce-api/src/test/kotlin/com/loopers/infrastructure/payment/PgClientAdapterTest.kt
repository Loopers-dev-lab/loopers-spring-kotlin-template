package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.CardType
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

class PgClientAdapterTest {

    private val pgGateway: PgGateway = mockk()
    private val callbackBaseUrl = "https://callback.example.com"
    private val adapter = PgClientAdapter(pgGateway, callbackBaseUrl)

    @DisplayName("requestPayment 테스트")
    @Nested
    inner class RequestPayment {

        @DisplayName("0원 결제 요청 시 pgGateway 호출 없이 NotRequired를 반환한다")
        @Test
        fun `returns NotRequired without calling pgGateway when amount is zero`() {
            // given
            val request = createPaymentRequest(amount = Money.ZERO_KRW)

            // when
            val result = adapter.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.NotRequired)
            verify(exactly = 0) { pgGateway.requestPayment(any()) }
        }

        @DisplayName("pgGateway 호출 성공 시 Accepted를 반환한다")
        @Test
        fun `returns Accepted when pgGateway call succeeds`() {
            // given
            val request = createPaymentRequest(amount = Money.krw(10000))
            val transactionKey = "tx_12345"
            every { pgGateway.requestPayment(any()) } returns PgPaymentResponse(
                transactionKey = transactionKey,
                status = "SUCCESS",
                reason = null,
            )

            // when
            val result = adapter.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.Accepted(transactionKey))
            verify(exactly = 1) { pgGateway.requestPayment(any()) }
        }

        @DisplayName("PgResponseUncertainException 발생 시 Uncertain을 반환한다")
        @Test
        fun `returns Uncertain when PgResponseUncertainException is thrown`() {
            // given
            val request = createPaymentRequest(amount = Money.krw(10000))
            every { pgGateway.requestPayment(any()) } throws PgResponseUncertainException("응답 불확실")

            // when
            val result = adapter.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.Uncertain)
        }

        @DisplayName("PgRequestNotReachedException 발생 시 NotReached를 반환한다")
        @Test
        fun `returns NotReached when PgRequestNotReachedException is thrown`() {
            // given
            val request = createPaymentRequest(amount = Money.krw(10000))
            every { pgGateway.requestPayment(any()) } throws PgRequestNotReachedException("연결 실패")

            // when
            val result = adapter.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.NotReached)
        }

        @DisplayName("CallNotPermittedException 발생 시 NotReached를 반환한다")
        @Test
        fun `returns NotReached when CallNotPermittedException is thrown`() {
            // given
            val request = createPaymentRequest(amount = Money.krw(10000))
            every { pgGateway.requestPayment(any()) } throws mockk<CallNotPermittedException>()

            // when
            val result = adapter.requestPayment(request)

            // then
            assertThat(result).isEqualTo(PgPaymentCreateResult.NotReached)
        }
    }

    @DisplayName("findTransaction 테스트")
    @Nested
    inner class FindTransaction {

        @DisplayName("pgGateway를 통해 트랜잭션을 조회한다")
        @Test
        fun `finds transaction via pgGateway`() {
            // given
            val transactionKey = "tx_12345"
            every { pgGateway.findTransaction(transactionKey) } returns PgPaymentDetailResponse(
                transactionKey = transactionKey,
                orderId = "000001",
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
                amount = 10000L,
                status = "SUCCESS",
                reason = null,
            )

            // when
            val result = adapter.findTransaction(transactionKey)

            // then
            val expectedTransaction = PgTransaction(
                transactionKey = transactionKey,
                paymentId = 1L,
                status = PgTransactionStatus.SUCCESS,
                failureReason = null,
            )
            assertThat(result).isEqualTo(expectedTransaction)
            verify(exactly = 1) { pgGateway.findTransaction(transactionKey) }
        }
    }

    @DisplayName("findTransactionsByPaymentId 테스트")
    @Nested
    inner class FindTransactionsByPaymentId {

        @DisplayName("pgGateway를 통해 트랜잭션 목록을 조회한다")
        @Test
        fun `finds transactions by payment id via pgGateway`() {
            // given
            val paymentId = 1L
            every { pgGateway.findTransactionsByOrderId("000001") } returns PgPaymentListResponse(
                orderId = "000001",
                transactions = listOf(
                    PgTransactionSummary(transactionKey = "tx_12345", status = "SUCCESS", reason = null),
                    PgTransactionSummary(transactionKey = "tx_67890", status = "FAILED", reason = "잔액 부족"),
                ),
            )

            // when
            val result = adapter.findTransactionsByPaymentId(paymentId)

            // then
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
            assertThat(result).isEqualTo(expectedTransactions)
            verify(exactly = 1) { pgGateway.findTransactionsByOrderId("000001") }
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
