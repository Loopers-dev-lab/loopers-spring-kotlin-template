package com.loopers.domain.payment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.TestPropertySource
import java.time.Instant

/**
 * PaymentService 통합 테스트
 *
 * 검증 범위:
 * - 결제 생성 및 상태 전이 (PENDING → IN_PROGRESS → PAID/FAILED)
 * - PG API 호출 및 응답 처리 (enum 변환, 예외 처리)
 * - 트랜잭션 조회 및 결제 확정 로직
 * - CircuitBreaker/Retry 동작 (infra layer)
 */
@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
@DisplayName("PaymentService 통합 테스트")
class PaymentServiceIntegrationTest @Autowired constructor(
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @BeforeEach
    fun setup() {
        for (circuitBreaker in circuitBreakerRegistry.allCircuitBreakers) {
            circuitBreaker.reset()
        }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        reset()
    }

    @Nested
    @DisplayName("create")
    inner class Create {

        @Test
        @DisplayName("PENDING 상태의 결제를 생성할 수 있다")
        fun `create pending payment successfully`() {
            // given
            val order = createOrder()
            val usedPoint = Money.krw(5000)

            // when
            val payment = paymentService.create(
                PaymentCommand.Create(
                    userId = order.userId,
                    orderId = order.id,
                    totalAmount = order.totalAmount,
                    usedPoint = usedPoint,
                    issuedCouponId = null,
                    couponDiscount = Money.ZERO_KRW,
                ),
            )

            // then - paidAmount = 10000 - 5000 = 5000 자동 계산
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.usedPoint).isEqualTo(usedPoint) },
                { assertThat(payment.paidAmount).isEqualTo(Money.krw(5000)) },
                { assertThat(payment.orderId).isEqualTo(order.id) },
            )
        }

        @Test
        @DisplayName("쿠폰 할인을 포함한 PENDING 결제를 생성할 수 있다")
        fun `create pending payment with coupon discount`() {
            // given
            val order = createOrder(totalAmount = Money.krw(15000))
            val usedPoint = Money.krw(5000)
            val couponDiscount = Money.krw(3000)

            // when
            val payment = paymentService.create(
                PaymentCommand.Create(
                    userId = order.userId,
                    orderId = order.id,
                    totalAmount = order.totalAmount,
                    usedPoint = usedPoint,
                    issuedCouponId = 1L,
                    couponDiscount = couponDiscount,
                ),
            )

            // then - paidAmount = 15000 - 5000 - 3000 = 7000 자동 계산
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.couponDiscount).isEqualTo(couponDiscount) },
                { assertThat(payment.issuedCouponId).isEqualTo(1L) },
                { assertThat(payment.paidAmount).isEqualTo(Money.krw(7000)) },
            )
        }

        @Test
        @DisplayName("포인트+쿠폰으로 전액 결제해도 PENDING 상태로 생성된다")
        fun `create PENDING payment even when fully covered by point and coupon`() {
            // given
            val order = createOrder(totalAmount = Money.krw(10000))

            // when
            val payment = paymentService.create(
                PaymentCommand.Create(
                    userId = order.userId,
                    orderId = order.id,
                    totalAmount = order.totalAmount,
                    usedPoint = Money.krw(7000),
                    issuedCouponId = 1L,
                    couponDiscount = Money.krw(3000),
                ),
            )

            // then - 0원 결제도 PENDING으로 생성, requestPgPayment() 호출 시 PAID로 전이
            assertAll(
                { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(payment.paidAmount).isEqualTo(Money.ZERO_KRW) },
            )
        }
    }

    @Nested
    @DisplayName("findPayments")
    inner class FindPayments {

        @Test
        @DisplayName("상태별 결제를 조회할 수 있다")
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

        @Test
        @DisplayName("pagination이 동작한다")
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

    @Nested
    @DisplayName("requestPgPayment")
    inner class RequestPgPayment {

        @Test
        @DisplayName("PG 결제 성공 시 IN_PROGRESS로 전이되고 transactionKey가 저장된다")
        fun `transitions to IN_PROGRESS with transactionKey when PG returns success`() {
            // given
            val payment = createPendingPayment()
            val cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456")
            val transactionKey = "tx_test_123"

            stubPgPaymentSuccess(transactionKey)

            // when
            val result = paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = cardInfo,
                ),
            )

            // then
            assertThat(result).isInstanceOf(PgPaymentResult.Success::class.java)
            val successResult = result as PgPaymentResult.Success
            assertAll(
                { assertThat(successResult.payment.status).isEqualTo(PaymentStatus.IN_PROGRESS) },
                { assertThat(successResult.payment.externalPaymentKey).isEqualTo(transactionKey) },
                { assertThat(successResult.payment.attemptedAt).isNotNull() },
            )

            // DB에도 반영되었는지 확인
            val savedPayment = paymentRepository.findById(payment.id)!!
            assertThat(savedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(savedPayment.externalPaymentKey).isEqualTo(transactionKey)
        }

        @Test
        @DisplayName("PG 응답 data가 null이면 Failed 결과를 반환한다")
        fun `returns Failed when PG response data is null`() {
            // given
            val payment = createPendingPayment()
            val cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456")

            stubPgPaymentDataNull()

            // when
            val result = paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = cardInfo,
                ),
            )

            // then - data null → NotReached → Failed
            assertThat(result).isInstanceOf(PgPaymentResult.Failed::class.java)
            val failedResult = result as PgPaymentResult.Failed
            assertThat(failedResult.payment.status).isEqualTo(PaymentStatus.FAILED)
        }

        @Test
        @DisplayName("PG 서버 에러(5xx) 시 Failed 결과를 반환한다")
        fun `returns Failed when PG server error occurs`() {
            // given
            val payment = createPendingPayment()
            val cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456")

            stubPgPaymentServerError()

            // when
            val result = paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = cardInfo,
                ),
            )

            // then
            assertThat(result).isInstanceOf(PgPaymentResult.Failed::class.java)
        }

        @Test
        @DisplayName("존재하지 않는 결제 ID로 요청하면 예외가 발생한다")
        fun `throws exception when payment not found`() {
            // given
            val cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456")

            // when
            val exception = assertThrows<CoreException> {
                paymentService.requestPgPayment(
                    PaymentCommand.RequestPgPayment(
                        paymentId = 999L,
                        cardInfo = cardInfo,
                    ),
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @Test
        @DisplayName("0원 결제 시 PG 호출 없이 즉시 완료된다")
        fun `completes immediately without PG call for zero amount payment`() {
            // given - paidAmount가 0인 결제
            val order = createOrder(totalAmount = Money.krw(10000))
            val payment = paymentRepository.save(
                Payment.create(
                    userId = order.userId,
                    orderId = order.id,
                    totalAmount = order.totalAmount,
                    usedPoint = Money.krw(10000),
                    issuedCouponId = null,
                    couponDiscount = Money.ZERO_KRW,
                ),
            )

            // when
            val result = paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = null,
                ),
            )

            // then - NotRequired → Success (이미 PAID 상태 유지)
            assertThat(result).isInstanceOf(PgPaymentResult.Success::class.java)
        }
    }

    @Nested
    @DisplayName("processCallback")
    inner class ProcessCallback {

        @Test
        @DisplayName("SUCCESS 트랜잭션 콜백을 받으면 PAID로 전이된다")
        fun `transitions to PAID when PG callback returns SUCCESS`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_success")

            stubPgTransactionQuery(
                transactionKey = "tx_callback_success",
                paymentId = payment.id,
                status = "SUCCESS",
            )

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_success",
            )

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Paid::class.java)
            val paidResult = result as ConfirmResult.Paid
            assertThat(paidResult.payment.status).isEqualTo(PaymentStatus.PAID)

            // DB에도 반영되었는지 확인
            val savedPayment = paymentRepository.findById(payment.id)!!
            assertThat(savedPayment.status).isEqualTo(PaymentStatus.PAID)
        }

        @Test
        @DisplayName("FAILED 트랜잭션 콜백을 받으면 FAILED로 전이된다")
        fun `transitions to FAILED when PG callback returns FAILED`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_failed")

            stubPgTransactionQuery(
                transactionKey = "tx_callback_failed",
                paymentId = payment.id,
                status = "FAILED",
                reason = "잔액 부족",
            )

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_failed",
            )

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Failed::class.java)
            val failedResult = result as ConfirmResult.Failed
            assertAll(
                { assertThat(failedResult.payment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(failedResult.payment.failureMessage).isEqualTo("잔액 부족") },
            )
        }

        @Test
        @DisplayName("PENDING 트랜잭션이면 StillInProgress 결과를 반환한다")
        fun `returns StillInProgress when PG transaction is still PENDING`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_pending")

            stubPgTransactionQuery(
                transactionKey = "tx_callback_pending",
                paymentId = payment.id,
                status = "PENDING",
            )

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_pending",
            )

            // then
            assertThat(result).isInstanceOf(ConfirmResult.StillInProgress::class.java)
            val stillInProgress = result as ConfirmResult.StillInProgress
            assertThat(stillInProgress.payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }

        @Test
        @DisplayName("이미 PAID 상태인 결제에 콜백이 오면 Paid를 반환한다")
        fun `returns AlreadyConfirmed when payment is already PAID`() {
            // given
            val payment = createPaidPayment()

            stubPgTransactionQuery(
                transactionKey = "tx_already_paid",
                paymentId = payment.id,
                status = "SUCCESS",
            )

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_already_paid",
            )

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Paid::class.java)
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 콜백이 오면 예외가 발생한다")
        fun `throws exception when order not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.processCallback(
                    orderId = 999L,
                    externalPaymentKey = "tx_not_found",
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("processInProgressPayment")
    inner class ProcessInProgressPayment {

        @Test
        @DisplayName("externalPaymentKey로 SUCCESS 트랜잭션을 찾으면 PAID로 전이된다")
        fun `transitions to PAID when finding SUCCESS transaction by externalPaymentKey`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_scheduler_success")

            stubPgTransactionQuery(
                transactionKey = "tx_scheduler_success",
                paymentId = payment.id,
                status = "SUCCESS",
            )

            // when
            val result = paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Paid::class.java)
            val paidResult = result as ConfirmResult.Paid
            assertThat(paidResult.payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @Test
        @DisplayName("externalPaymentKey로 FAILED 트랜잭션을 찾으면 FAILED로 전이된다")
        fun `transitions to FAILED when finding FAILED transaction by externalPaymentKey`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_scheduler_failed")
            val failureReason = "카드 한도 초과"

            stubPgTransactionQuery(
                transactionKey = "tx_scheduler_failed",
                paymentId = payment.id,
                status = "FAILED",
                reason = failureReason,
            )

            // when
            val result = paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Failed::class.java)
            val failedResult = result as ConfirmResult.Failed
            assertAll(
                { assertThat(failedResult.payment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(failedResult.payment.failureMessage).isEqualTo(failureReason) },
            )
        }

        @Test
        @DisplayName("externalPaymentKey가 없으면 paymentId(orderId)로 트랜잭션 목록을 조회한다")
        fun `queries transactions by orderId when externalPaymentKey is null`() {
            // given - Uncertain 결과로 생성 시 externalPaymentKey가 null
            val payment = createInProgressPayment(externalPaymentKey = null)

            stubPgTransactionListQuery(
                orderId = payment.id.toString().padStart(6, '0'),
                transactions = listOf(
                    TransactionSummary("tx_found", "SUCCESS", null),
                ),
            )

            // when
            val result = paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Paid::class.java)
        }

        @Test
        @DisplayName("트랜잭션 목록이 비어있으면 FAILED로 전이된다")
        fun `transitions to FAILED when transaction list is empty`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = null)

            stubPgTransactionListQuery(
                orderId = payment.id.toString().padStart(6, '0'),
                transactions = emptyList(),
            )

            // when
            val result = paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Failed::class.java)
            val failedResult = result as ConfirmResult.Failed
            assertThat(failedResult.payment.failureMessage).isEqualTo("매칭되는 PG 트랜잭션이 없습니다")
        }

        @Test
        @DisplayName("PENDING 트랜잭션이면 StillInProgress 결과를 반환한다")
        fun `returns StillInProgress when transaction is still PENDING`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_scheduler_pending")

            stubPgTransactionQuery(
                transactionKey = "tx_scheduler_pending",
                paymentId = payment.id,
                status = "PENDING",
            )

            // when
            val result = paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            assertThat(result).isInstanceOf(ConfirmResult.StillInProgress::class.java)
        }

        @Test
        @DisplayName("존재하지 않는 결제 ID면 예외가 발생한다")
        fun `throws exception when payment not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.processInProgressPayment(paymentId = 999L)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    // ===========================================
    // WireMock 스텁 헬퍼 - 결제 요청
    // ===========================================

    private fun stubPgPaymentSuccess(transactionKey: String) {
        stubFor(
            post(urlEqualTo("/api/v1/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                                "data": {
                                    "transactionKey": "$transactionKey",
                                    "status": "PENDING",
                                    "reason": null
                                }
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    private fun stubPgPaymentDataNull() {
        stubFor(
            post(urlEqualTo("/api/v1/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "meta": {"result": "FAIL", "errorCode": "PG_ERROR", "message": "결제 실패"},
                                "data": null
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    private fun stubPgPaymentServerError() {
        stubFor(
            post(urlEqualTo("/api/v1/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"error": "Internal Server Error"}"""),
                ),
        )
    }

    // ===========================================
    // WireMock 스텁 헬퍼 - 트랜잭션 조회
    // ===========================================

    private fun stubPgTransactionQuery(
        transactionKey: String,
        paymentId: Long,
        status: String,
        reason: String? = null,
    ) {
        val orderId = paymentId.toString().padStart(6, '0')
        stubFor(
            get(urlEqualTo("/api/v1/payments/$transactionKey"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                                "data": {
                                    "transactionKey": "$transactionKey",
                                    "orderId": "$orderId",
                                    "cardType": "KB",
                                    "cardNo": "1234567890123456",
                                    "amount": 5000,
                                    "status": "$status",
                                    "reason": ${reason?.let { "\"$it\"" } ?: "null"}
                                }
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    private data class TransactionSummary(
        val transactionKey: String,
        val status: String,
        val reason: String?,
    )

    private fun stubPgTransactionListQuery(
        orderId: String,
        transactions: List<TransactionSummary>,
    ) {
        val transactionsJson = transactions.joinToString(",") { tx ->
            """
            {
                "transactionKey": "${tx.transactionKey}",
                "status": "${tx.status}",
                "reason": ${tx.reason?.let { "\"$it\"" } ?: "null"}
            }
            """.trimIndent()
        }

        stubFor(
            get(urlPathEqualTo("/api/v1/payments"))
                .withQueryParam("orderId", equalTo(orderId))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                                "data": {
                                    "orderId": "$orderId",
                                    "transactions": [$transactionsJson]
                                }
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    // ===========================================
    // 도메인 픽스처 헬퍼
    // ===========================================

    private fun createOrder(
        userId: Long = 1L,
        totalAmount: Money = Money.krw(10000),
    ): Order {
        val order = Order.place(userId)
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
            orderId = order.id,
            totalAmount = order.totalAmount,
            usedPoint = usedPoint,
            issuedCouponId = null,
            couponDiscount = Money.ZERO_KRW,
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

    private fun createPaidPayment(): Payment {
        val payment = createInProgressPayment(externalPaymentKey = "tx_paid")
        stubPgTransactionQuery(
            transactionKey = "tx_paid",
            paymentId = payment.id,
            status = "SUCCESS",
        )
        val transaction = PgTransaction(
            transactionKey = "tx_paid",
            paymentId = payment.id,
            status = PgTransactionStatus.SUCCESS,
            failureReason = null,
        )
        payment.confirmPayment(listOf(transaction), Instant.now())
        return paymentRepository.save(payment)
    }
}
