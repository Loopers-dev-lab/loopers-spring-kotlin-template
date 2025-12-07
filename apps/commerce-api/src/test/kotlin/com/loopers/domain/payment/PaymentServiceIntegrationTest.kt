package com.loopers.domain.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
class PaymentServiceIntegrationTest @Autowired constructor(
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var pgClient: PgClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("PENDING 결제 생성 통합테스트")
    @Nested
    inner class CreatePending {

        @DisplayName("PENDING 상태의 결제를 생성할 수 있다")
        @Test
        fun `create pending payment successfully`() {
            // given
            val order = createOrder() // totalAmount = 10000
            val usedPoint = Money.krw(5000)

            // when
            val payment = paymentService.createPending(
                userId = order.userId,
                order = order,
                usedPoint = usedPoint,
            )

            // then - paidAmount = 10000 - 5000 = 5000 자동 계산
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.usedPoint).isEqualTo(usedPoint) },
                { assertThat(payment.paidAmount).isEqualTo(Money.krw(5000)) },
                { assertThat(payment.orderId).isEqualTo(order.id) },
            )
        }

        @DisplayName("쿠폰 할인을 포함한 PENDING 결제를 생성할 수 있다")
        @Test
        fun `create pending payment with coupon discount`() {
            // given
            val order = createOrder(totalAmount = Money.krw(15000))
            val usedPoint = Money.krw(5000)
            val couponDiscount = Money.krw(3000)

            // when
            val payment = paymentService.createPending(
                userId = order.userId,
                order = order,
                usedPoint = usedPoint,
                issuedCouponId = 1L,
                couponDiscount = couponDiscount,
            )

            // then - paidAmount = 15000 - 5000 - 3000 = 7000 자동 계산
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.couponDiscount).isEqualTo(couponDiscount) },
                { assertThat(payment.issuedCouponId).isEqualTo(1L) },
                { assertThat(payment.paidAmount).isEqualTo(Money.krw(7000)) },
            )
        }
    }

    @DisplayName("PG 결제 요청 통합테스트")
    @Nested
    inner class RequestPgPayment {

        @DisplayName("Accepted 결과 시 IN_PROGRESS로 전이되고 transactionKey가 저장된다")
        @Test
        fun `request pg payment with Accepted transitions to IN_PROGRESS with transactionKey`() {
            // given
            val payment = createPendingPayment()
            val cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456")
            val transactionKey = "tx_test_123"

            every { pgClient.requestPayment(any()) } returns PgPaymentCreateResult.Accepted(transactionKey)

            // when
            val updatedPayment = paymentService.requestPgPayment(
                paymentId = payment.id,
                cardInfo = cardInfo,
            )

            // then
            assertAll(
                { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS) },
                { assertThat(updatedPayment.externalPaymentKey).isEqualTo(transactionKey) },
                { assertThat(updatedPayment.attemptedAt).isNotNull() },
            )
        }

        @DisplayName("Uncertain 결과 시 IN_PROGRESS로 전이되고 transactionKey는 null이다")
        @Test
        fun `request pg payment with Uncertain transitions to IN_PROGRESS without transactionKey`() {
            // given
            val payment = createPendingPayment()
            val cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456")

            every { pgClient.requestPayment(any()) } returns PgPaymentCreateResult.Uncertain

            // when
            val updatedPayment = paymentService.requestPgPayment(
                paymentId = payment.id,
                cardInfo = cardInfo,
            )

            // then
            assertAll(
                { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS) },
                { assertThat(updatedPayment.externalPaymentKey).isNull() },
                { assertThat(updatedPayment.attemptedAt).isNotNull() },
            )
        }

        @DisplayName("존재하지 않는 결제 ID로 요청하면 예외가 발생한다")
        @Test
        fun `throw exception when payment not found`() {
            // given
            val cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456")

            // when
            val exception = assertThrows<CoreException> {
                paymentService.requestPgPayment(999L, cardInfo)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("결제 확정 (IN_PROGRESS -> PAID/FAILED) 통합테스트")
    @Nested
    inner class ConfirmPayment {

        @DisplayName("SUCCESS 트랜잭션으로 확정하면 PAID로 전이된다")
        @Test
        fun `confirm payment with SUCCESS transaction transitions to PAID`() {
            // given
            val payment = createInProgressPayment()
            val transaction = createTransaction(
                transactionKey = "tx_test",
                status = PgTransactionStatus.SUCCESS,
            )

            // when
            val updatedPayment = paymentService.confirmPayment(
                paymentId = payment.id,
                transactions = listOf(transaction),
                currentTime = Instant.now(),
            )

            // then
            assertAll(
                { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PAID) },
                { assertThat(updatedPayment.externalPaymentKey).isEqualTo("tx_test") },
            )
        }

        @DisplayName("FAILED 트랜잭션으로 확정하면 FAILED로 전이된다")
        @Test
        fun `confirm payment with FAILED transaction transitions to FAILED`() {
            // given
            val payment = createInProgressPayment()
            val failureReason = "잔액 부족"
            val transaction = createTransaction(
                transactionKey = "tx_test",
                status = PgTransactionStatus.FAILED,
                failureReason = failureReason,
            )

            // when
            val updatedPayment = paymentService.confirmPayment(
                paymentId = payment.id,
                transactions = listOf(transaction),
                currentTime = Instant.now(),
            )

            // then
            assertAll(
                { assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(updatedPayment.failureMessage).isEqualTo(failureReason) },
            )
        }

        @DisplayName("존재하지 않는 결제 ID로 확정하면 예외가 발생한다")
        @Test
        fun `throw exception when payment not found`() {
            // given
            val transaction = createTransaction()

            // when
            val exception = assertThrows<CoreException> {
                paymentService.confirmPayment(
                    paymentId = 999L,
                    transactions = listOf(transaction),
                    currentTime = Instant.now(),
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("결제 조회 통합테스트 (pagination 지원)")
    @Nested
    inner class FindPayments {

        @DisplayName("상태별 결제를 조회할 수 있다")
        @Test
        fun `find payments by status`() {
            // given
            val inProgressPayment = createInProgressPayment()

            // when
            val command = PaymentCommand.FindPayments(
                statuses = listOf(PaymentStatus.IN_PROGRESS),
            )
            val payments = paymentService.findPayments(command)

            // then
            assertThat(payments.content).hasSize(1)
            assertThat(payments.content[0].id).isEqualTo(inProgressPayment.id)
        }

        @DisplayName("pagination이 동작한다")
        @Test
        fun `pagination works correctly`() {
            // given
            repeat(5) { index -> createInProgressPayment(externalPaymentKey = "tx_pagination_$index") }

            // when
            val command = PaymentCommand.FindPayments(
                statuses = listOf(PaymentStatus.IN_PROGRESS),
                page = 0,
                size = 2,
            )
            val payments = paymentService.findPayments(command)

            // then
            assertThat(payments.content).hasSize(2)
            assertThat(payments.hasNext()).isTrue()
        }
    }

    @DisplayName("외부 결제 키로 결제 조회 통합테스트")
    @Nested
    inner class FindByExternalPaymentKey {

        @DisplayName("외부 결제 키로 결제를 조회할 수 있다")
        @Test
        fun `find payment by external key`() {
            // given - createInProgressPayment()에서 이미 externalPaymentKey가 설정됨
            val payment = createInProgressPayment(externalPaymentKey = "pg_tx_12345")

            // when
            val foundPayment = paymentService.findByExternalPaymentKey("pg_tx_12345")

            // then
            assertThat(foundPayment).isNotNull
            assertThat(foundPayment?.id).isEqualTo(payment.id)
        }

        @DisplayName("존재하지 않는 외부 결제 키로 조회하면 null을 반환한다")
        @Test
        fun `return null when external key not found`() {
            // when
            val foundPayment = paymentService.findByExternalPaymentKey("non_existent_key")

            // then
            assertThat(foundPayment).isNull()
        }
    }

    @DisplayName("PG 콜백 처리 통합테스트")
    @Nested
    inner class ProcessCallback {

        @DisplayName("SUCCESS 트랜잭션 콜백을 받으면 PAID로 전이된다")
        @Test
        fun `transitions to PAID when SUCCESS callback received`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "pg_tx_success")
            val successTransaction = createTransaction(
                transactionKey = "pg_tx_success",
                status = PgTransactionStatus.SUCCESS,
            )
            every { pgClient.findTransaction("pg_tx_success") } returns successTransaction

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "pg_tx_success",
            )

            // then
            assertThat(result).isInstanceOf(PaymentService.CallbackResult.Confirmed::class.java)
            val confirmed = result as PaymentService.CallbackResult.Confirmed
            assertThat(confirmed.payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("FAILED 트랜잭션 콜백을 받으면 FAILED로 전이된다")
        @Test
        fun `transitions to FAILED when FAILED callback received`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "pg_tx_failed")
            val failureReason = "카드 한도 초과"
            val failedTransaction = createTransaction(
                transactionKey = "pg_tx_failed",
                status = PgTransactionStatus.FAILED,
                failureReason = failureReason,
            )
            every { pgClient.findTransaction("pg_tx_failed") } returns failedTransaction

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "pg_tx_failed",
            )

            // then
            assertThat(result).isInstanceOf(PaymentService.CallbackResult.Confirmed::class.java)
            val confirmed = result as PaymentService.CallbackResult.Confirmed
            assertThat(confirmed.payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(confirmed.payment.failureMessage).isEqualTo(failureReason)
        }

        @DisplayName("이미 처리된 결제에 콜백이 오면 AlreadyProcessed를 반환한다")
        @Test
        fun `returns AlreadyProcessed when payment already processed`() {
            // given
            val payment = createPaidPayment()
            val transaction = createTransaction(
                transactionKey = "pg_tx_already",
                status = PgTransactionStatus.SUCCESS,
            )
            every { pgClient.findTransaction("pg_tx_already") } returns transaction

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "pg_tx_already",
            )

            // then
            assertThat(result).isInstanceOf(PaymentService.CallbackResult.AlreadyProcessed::class.java)
        }

        @DisplayName("존재하지 않는 주문 ID로 콜백이 오면 예외가 발생한다")
        @Test
        fun `throws exception when order not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.processCallback(
                    orderId = 999L,
                    externalPaymentKey = "pg_tx_not_found",
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("IN_PROGRESS 결제 처리 통합테스트")
    @Nested
    inner class ProcessInProgressPayment {

        @DisplayName("매칭되는 SUCCESS 트랜잭션이 있으면 PAID로 전이된다")
        @Test
        fun `transitions to PAID when matching SUCCESS transaction found`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "pg_tx_match")
            val successTransaction = createTransaction(
                transactionKey = "pg_tx_match",
                status = PgTransactionStatus.SUCCESS,
            )
            every { pgClient.findTransaction("pg_tx_match") } returns successTransaction

            // when
            val result = paymentService.processInProgressPayment(
                paymentId = payment.id,
            )

            // then
            assertThat(result).isInstanceOf(PaymentService.CallbackResult.Confirmed::class.java)
            val confirmed = result as PaymentService.CallbackResult.Confirmed
            assertThat(confirmed.payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @DisplayName("매칭되는 FAILED 트랜잭션이 있으면 FAILED로 전이된다")
        @Test
        fun `transitions to FAILED when matching FAILED transaction found`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "pg_tx_fail_match")
            val failureReason = "잔액 부족"
            val failedTransaction = createTransaction(
                transactionKey = "pg_tx_fail_match",
                status = PgTransactionStatus.FAILED,
                failureReason = failureReason,
            )
            every { pgClient.findTransaction("pg_tx_fail_match") } returns failedTransaction

            // when
            val result = paymentService.processInProgressPayment(
                paymentId = payment.id,
            )

            // then
            assertThat(result).isInstanceOf(PaymentService.CallbackResult.Confirmed::class.java)
            val confirmed = result as PaymentService.CallbackResult.Confirmed
            assertThat(confirmed.payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(confirmed.payment.failureMessage).isEqualTo(failureReason)
        }

        @DisplayName("매칭되는 트랜잭션이 없으면 FAILED로 전이된다")
        @Test
        fun `transitions to FAILED when no matching transaction exists`() {
            // given
            // externalPaymentKey가 없으면 findTransactionsByPaymentId로 fallback
            val payment = createInProgressPayment(externalPaymentKey = null)
            // paymentId가 다른 트랜잭션 (매칭 안됨)
            // externalPaymentKey가 null이면 paymentId로 매칭하므로 다르게 설정
            val unmatchedTransaction = createTransaction(
                transactionKey = "pg_tx_unmatched",
                paymentId = payment.id + 1,
                status = PgTransactionStatus.SUCCESS,
            )
            every { pgClient.findTransactionsByPaymentId(payment.id) } returns listOf(unmatchedTransaction)

            // when
            val result = paymentService.processInProgressPayment(
                paymentId = payment.id,
            )

            // then
            assertThat(result).isInstanceOf(PaymentService.CallbackResult.Confirmed::class.java)
            val confirmed = result as PaymentService.CallbackResult.Confirmed
            assertThat(confirmed.payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(confirmed.payment.failureMessage).isEqualTo("매칭되는 PG 트랜잭션이 없습니다")
        }

        @DisplayName("존재하지 않는 결제 ID면 예외가 발생한다")
        @Test
        fun `throws exception when payment not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.processInProgressPayment(
                    paymentId = 999L,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("결제 ID로 조회 통합테스트")
    @Nested
    inner class FindById {

        @DisplayName("존재하는 결제를 조회할 수 있다")
        @Test
        fun `find existing payment by id`() {
            // given
            val payment = createPendingPayment()

            // when
            val foundPayment = paymentService.findById(payment.id)

            // then
            assertThat(foundPayment.id).isEqualTo(payment.id)
            assertThat(foundPayment.status).isEqualTo(PaymentStatus.PENDING)
        }

        @DisplayName("존재하지 않는 결제 ID면 예외가 발생한다")
        @Test
        fun `throws exception when payment not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.findById(999L)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("주문 ID로 결제 조회 통합테스트")
    @Nested
    inner class FindByOrderId {

        @DisplayName("존재하는 결제를 조회할 수 있다")
        @Test
        fun `find existing payment by order id`() {
            // given
            val payment = createPendingPayment()

            // when
            val foundPayment = paymentService.findByOrderId(payment.orderId)

            // then
            assertThat(foundPayment.id).isEqualTo(payment.id)
            assertThat(foundPayment.orderId).isEqualTo(payment.orderId)
        }

        @DisplayName("존재하지 않는 주문 ID면 예외가 발생한다")
        @Test
        fun `throws exception when order not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.findByOrderId(999L)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    private fun createOrder(
        userId: Long = 1L,
        totalAmount: Money = Money.krw(10000),
    ): Order {
        val order = Order.Companion.place(userId)
        order.addOrderItem(
            productId = 1L,
            quantity = 1,
            productName = "테스트 상품",
            unitPrice = totalAmount,
        )
        return orderRepository.save(order)
    }

    private fun createPendingPayment(
        userId: Long = 1L,
        usedPoint: Money = Money.krw(5000),
    ): Payment {
        val order = createOrder()
        val payment = Payment.create(
            userId = userId,
            order = order,
            usedPoint = usedPoint,
        )
        return paymentRepository.save(payment)
    }

    private fun createInProgressPayment(externalPaymentKey: String? = "tx_test"): Payment {
        val payment = createPendingPayment()
        val result = if (externalPaymentKey != null) {
            PgPaymentCreateResult.Accepted(externalPaymentKey)
        } else {
            PgPaymentCreateResult.Uncertain
        }
        payment.initiate(result, Instant.now())
        return paymentRepository.save(payment)
    }

    private fun createInProgressPaymentWithAttemptedAt(
        externalPaymentKey: String = "tx_test",
        attemptedAt: Instant,
    ): Payment {
        val payment = createPendingPayment()
        payment.initiate(PgPaymentCreateResult.Accepted(externalPaymentKey), attemptedAt)
        return paymentRepository.save(payment)
    }

    private fun createPaidPayment(): Payment {
        val payment = createInProgressPayment(externalPaymentKey = "tx_paid")
        val successTransaction = createTransaction(
            transactionKey = "tx_paid",
            status = PgTransactionStatus.SUCCESS,
        )
        payment.confirmPayment(successTransaction, currentTime = Instant.now())
        return paymentRepository.save(payment)
    }

    private fun createTransaction(
        transactionKey: String = "tx_default",
        paymentId: Long = 1L,
        status: PgTransactionStatus = PgTransactionStatus.SUCCESS,
        failureReason: String? = null,
    ): PgTransaction {
        return PgTransaction(
            transactionKey = transactionKey,
            paymentId = paymentId,
            status = status,
            failureReason = failureReason,
        )
    }
}
