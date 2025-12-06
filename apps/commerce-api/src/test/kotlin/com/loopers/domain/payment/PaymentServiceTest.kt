package com.loopers.domain.payment

import com.loopers.domain.event.PaymentCompletedEvent
import com.loopers.domain.event.PaymentFailedEvent
import com.loopers.infrastructure.payment.client.CardTypeDto
import com.loopers.infrastructure.payment.client.PgApiResponse
import com.loopers.infrastructure.payment.client.PgClient
import com.loopers.infrastructure.payment.client.PgErrorResponse
import com.loopers.infrastructure.payment.client.PgTransactionDetailResponse
import com.loopers.infrastructure.payment.client.PgTransactionResponse
import com.loopers.infrastructure.payment.client.TransactionStatusDto
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher

class PaymentServiceTest {
    private val paymentRepository = mockk<PaymentRepository>()
    private val pgClient = mockk<PgClient>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val paymentService = PaymentService(
        paymentRepository,
        pgClient,
        eventPublisher,
        callbackBaseUrl = "http://localhost:8080",
    )

    @DisplayName("결제를 생성할 때,")
    @Nested
    inner class CreatePayment {
        @DisplayName("결제 정보를 저장한다")
        @Test
        fun createPayment_savesPayment() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )

            every { paymentRepository.save(any()) } returns payment

            // when
            val result = paymentService.createPayment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )

            // then
            assertThat(result.userId).isEqualTo(1L)
            assertThat(result.orderId).isEqualTo(100L)
            assertThat(result.amount).isEqualTo(10000L)
            verify(exactly = 1) { paymentRepository.save(any()) }
        }
    }

    @DisplayName("PG 결제를 요청할 때,")
    @Nested
    inner class RequestPgPayment {
        @DisplayName("카드 정보가 없으면 예외가 발생한다")
        @Test
        fun requestPgPayment_whenNoCardInfo_thenThrowsException() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
            )

            // when & then
            val exception = assertThrows<CoreException> {
                paymentService.requestPgPayment(payment)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("카드 결제 시 카드 정보는 필수입니다")
        }

        @DisplayName("유효하지 않은 카드 타입이면 예외가 발생한다")
        @Test
        fun requestPgPayment_whenInvalidCardType_thenThrowsException() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "INVALID_TYPE",
                cardNo = "1234-5678-9012-3456",
            )

            // when & then
            val exception = assertThrows<CoreException> {
                paymentService.requestPgPayment(payment)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("유효하지 않은 카드 타입입니다")
        }

        @DisplayName("PG 결제 요청에 성공하면 거래 키를 반환한다")
        @Test
        fun requestPgPayment_whenSuccess_thenReturnsTransactionKey() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )

            val pgResponse = PgApiResponse(
                success = true,
                data = PgTransactionResponse(
                    transactionKey = "txn_123456",
                    status = TransactionStatusDto.PENDING,
                    reason = null,
                ),
                error = null,
            )

            every { pgClient.requestPayment(any(), any()) } returns pgResponse

            // when
            val result = paymentService.requestPgPayment(payment)

            // then
            assertThat(result).isEqualTo("txn_123456")
            verify(exactly = 1) { pgClient.requestPayment(any(), any()) }
        }

        @DisplayName("PG 결제 요청이 실패하면 예외가 발생한다")
        @Test
        fun requestPgPayment_whenFailed_thenThrowsException() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )

            val pgResponse = PgApiResponse<PgTransactionResponse>(
                success = false,
                data = null,
                error = PgErrorResponse(code = "PG_ERROR", message = "PG 시스템 오류"),
            )

            every { pgClient.requestPayment(any(), any()) } returns pgResponse

            // when & then
            val exception = assertThrows<CoreException> {
                paymentService.requestPgPayment(payment)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.INTERNAL_ERROR)
            assertThat(exception.message).contains("PG 시스템 오류")
        }
    }

    @DisplayName("거래 키를 업데이트할 때,")
    @Nested
    inner class UpdatePaymentWithTransaction {
        @DisplayName("결제를 찾을 수 없으면 예외가 발생한다")
        @Test
        fun updatePaymentWithTransaction_whenNotFound_thenThrowsException() {
            // given
            every { paymentRepository.findById(999L) } returns null

            // when & then
            val exception = assertThrows<CoreException> {
                paymentService.updatePaymentWithTransaction(999L, "txn_123456")
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("결제 정보를 찾을 수 없습니다")
        }

        @DisplayName("거래 키를 업데이트한다")
        @Test
        fun updatePaymentWithTransaction_updatesTransactionKey() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )

            every { paymentRepository.findById(1L) } returns payment
            every { paymentRepository.save(any()) } answers { firstArg() }

            // when
            paymentService.updatePaymentWithTransaction(1L, "txn_123456")

            // then
            assertThat(payment.transactionKey).isEqualTo("txn_123456")
            verify(exactly = 1) { paymentRepository.save(payment) }
        }
    }

    @DisplayName("결제 콜백을 처리할 때,")
    @Nested
    inner class HandlePaymentCallback {
        @DisplayName("거래 키에 해당하는 결제를 찾을 수 없으면 예외가 발생한다")
        @Test
        fun handlePaymentCallback_whenNotFound_thenThrowsException() {
            // given
            every { paymentRepository.findByTransactionKey("invalid_key") } returns null

            // when & then
            val exception = assertThrows<CoreException> {
                paymentService.handlePaymentCallback("invalid_key", TransactionStatusDto.SUCCESS, null)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("거래 키에 해당하는 결제를 찾을 수 없습니다")
        }

        @DisplayName("결제 성공 시 결제를 완료하고 이벤트를 발행한다")
        @Test
        fun handlePaymentCallback_whenSuccess_thenCompletesPaymentAndPublishesEvent() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )

            every { paymentRepository.findByTransactionKey("txn_123456") } returns payment
            every { paymentRepository.save(any()) } answers { firstArg() }

            // when
            paymentService.handlePaymentCallback("txn_123456", TransactionStatusDto.SUCCESS, "승인 완료")

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETED)
            verify(exactly = 1) { paymentRepository.save(payment) }
            verify(exactly = 1) { eventPublisher.publishEvent(ofType(PaymentCompletedEvent::class)) }
        }

        @DisplayName("결제 실패 시 결제를 실패 처리하고 이벤트를 발행한다")
        @Test
        fun handlePaymentCallback_whenFailed_thenFailsPaymentAndPublishesEvent() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )
            every { paymentRepository.findByTransactionKey("txn_123456") } returns payment
            every { paymentRepository.save(any()) } answers { firstArg() }
            // when
            paymentService.handlePaymentCallback("txn_123456", TransactionStatusDto.FAILED, "카드 한도 초과")
            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureReason).isEqualTo("카드 한도 초과")
            verify(exactly = 1) { paymentRepository.save(payment) }
            verify(exactly = 1) { eventPublisher.publishEvent(ofType(PaymentFailedEvent::class)) }
        }

        @DisplayName("결제 대기 상태는 상태를 변경하지 않고 저장만 한다")
        @Test
        fun handlePaymentCallback_whenPending_thenDoesNothing() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )

            every { paymentRepository.findByTransactionKey("txn_123456") } returns payment
            every { paymentRepository.save(any()) } answers { firstArg() }

            // when
            paymentService.handlePaymentCallback("txn_123456", TransactionStatusDto.PENDING, null)

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            verify(exactly = 1) { paymentRepository.save(payment) }
            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }
    }

    @DisplayName("PG 결제 상태를 조회할 때,")
    @Nested
    inner class CheckPaymentStatus {
        @DisplayName("PG에서 상태 조회에 성공하면 상태를 반환한다")
        @Test
        fun checkPaymentStatus_whenSuccess_thenReturnsStatus() {
            // given
            val pgResponse = PgApiResponse(
                success = true,
                data = PgTransactionDetailResponse(
                    transactionKey = "txn_123456",
                    orderId = "100",
                    cardType = CardTypeDto.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                    amount = 10000L,
                    status = TransactionStatusDto.SUCCESS,
                    reason = null,
                ),
                error = null,
            )

            every { pgClient.getTransaction(any(), any()) } returns pgResponse

            // when
            val result = paymentService.checkPaymentStatus(1L, "txn_123456")

            // then
            assertThat(result).isEqualTo(TransactionStatusDto.SUCCESS)
            verify(exactly = 1) { pgClient.getTransaction("1", "txn_123456") }
        }

        @DisplayName("PG 상태 조회에 실패하면 예외가 발생한다")
        @Test
        fun checkPaymentStatus_whenFailed_thenThrowsException() {
            // given
            val pgResponse = PgApiResponse<PgTransactionDetailResponse>(
                success = false,
                data = null,
                error = PgErrorResponse(code = "PG_ERROR", message = "거래를 찾을 수 없습니다"),
            )

            every { pgClient.getTransaction(any(), any()) } returns pgResponse

            // when & then
            val exception = assertThrows<CoreException> {
                paymentService.checkPaymentStatus(1L, "txn_123456")
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.INTERNAL_ERROR)
            assertThat(exception.message).contains("거래를 찾을 수 없습니다")
        }
    }

    @DisplayName("결제를 조회할 때,")
    @Nested
    inner class GetPayment {
        @DisplayName("거래 키로 결제를 조회할 수 있다")
        @Test
        fun getPaymentByTransactionKey_returnsPayment() {
            // given
            val payment = Payment(
                userId = 1L,
                orderId = 100L,
                paymentMethod = PaymentMethod.CARD,
                amount = 10000L,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )

            every { paymentRepository.findByTransactionKey("txn_123456") } returns payment

            // when
            val result = paymentService.getPaymentByTransactionKey("txn_123456")

            // then
            assertThat(result).isEqualTo(payment)
        }

        @DisplayName("주문 ID로 결제 목록을 조회할 수 있다")
        @Test
        fun getPaymentsByOrderId_returnsPayments() {
            // given
            val payments = listOf(
                Payment(
                    userId = 1L,
                    orderId = 100L,
                    paymentMethod = PaymentMethod.CARD,
                    amount = 10000L,
                    cardType = "SAMSUNG",
                    cardNo = "1234-5678-9012-3456",
                ),
            )

            every { paymentRepository.findByOrderId(100L) } returns payments

            // when
            val result = paymentService.getPaymentsByOrderId(100L)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[0].orderId).isEqualTo(100L)
        }
    }
}
