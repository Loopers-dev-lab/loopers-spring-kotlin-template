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
 * - 결제 생성 및 상태 전이 오케스트레이션
 * - PG API 호출 결과에 따른 상태 전이
 * - 트랜잭션 조회 및 결제 확정 흐름
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
        fun `creates payment with PENDING status`() {
            // given
            val order = createOrder()
            val cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456")

            // when
            val payment = paymentService.create(
                PaymentCommand.Create(
                    userId = order.userId,
                    orderId = order.id,
                    totalAmount = order.totalAmount,
                    usedPoint = Money.krw(5000),
                    issuedCouponId = null,
                    couponDiscount = Money.ZERO_KRW,
                    cardInfo = cardInfo,
                ),
            )

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment.cardInfo).isEqualTo(cardInfo)
        }

        @Test
        @DisplayName("쿠폰을 포함한 결제를 생성할 수 있다")
        fun `creates payment with coupon`() {
            // given
            val order = createOrder(totalAmount = Money.krw(15000))
            val cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456")

            // when
            val payment = paymentService.create(
                PaymentCommand.Create(
                    userId = order.userId,
                    orderId = order.id,
                    totalAmount = order.totalAmount,
                    usedPoint = Money.krw(5000),
                    issuedCouponId = 1L,
                    couponDiscount = Money.krw(3000),
                    cardInfo = cardInfo,
                ),
            )

            // then
            assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
            assertThat(payment.cardInfo).isEqualTo(cardInfo)
        }
    }

    @Nested
    @DisplayName("findPayments")
    inner class FindPayments {

        @Test
        @DisplayName("상태별 결제를 조회할 수 있다")
        fun `finds payments by status`() {
            // given
            createInProgressPayment()

            // when
            val payments = paymentService.findPayments(
                PaymentCommand.FindPayments(statuses = listOf(PaymentStatus.IN_PROGRESS)),
            )

            // then
            assertThat(payments.content).hasSize(1)
        }

        @Test
        @DisplayName("pagination이 동작한다")
        fun `paginates correctly`() {
            // given
            repeat(5) { index -> createInProgressPayment(externalPaymentKey = "tx_pagination_$index") }

            // when
            val payments = paymentService.findPayments(
                PaymentCommand.FindPayments(
                    statuses = listOf(PaymentStatus.IN_PROGRESS),
                    page = 0,
                    size = 2,
                ),
            )

            // then
            assertThat(payments.content).hasSize(2)
            assertThat(payments.hasNext()).isTrue()
        }
    }

    @Nested
    @DisplayName("requestPgPayment")
    inner class RequestPgPayment {

        @Test
        @DisplayName("PG 결제 요청 성공 시 IN_PROGRESS로 전이된다")
        fun `transitions to IN_PROGRESS when PG accepts`() {
            // given
            val payment = createPendingPayment()
            stubPgPaymentSuccess("tx_test_123")

            // when
            val result = paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
                ),
            )

            // then
            assertThat(result).isInstanceOf(PgPaymentResult.InProgress::class.java)
            assertThat((result as PgPaymentResult.InProgress).payment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }

        @Test
        @DisplayName("PG 응답 data가 null이면 Failed를 반환한다")
        fun `returns Failed when PG response data is null`() {
            // given
            val payment = createPendingPayment()
            stubPgPaymentDataNull()

            // when
            val result = paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
                ),
            )

            // then
            assertThat(result).isInstanceOf(PgPaymentResult.Failed::class.java)
        }

        @Test
        @DisplayName("PG 서버 에러 시 Failed를 반환한다")
        fun `returns Failed when PG server error`() {
            // given
            val payment = createPendingPayment()
            stubPgPaymentServerError()

            // when
            val result = paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
                ),
            )

            // then
            assertThat(result).isInstanceOf(PgPaymentResult.Failed::class.java)
        }

        @Test
        @DisplayName("존재하지 않는 결제 ID로 요청하면 예외가 발생한다")
        fun `throws exception when payment not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.requestPgPayment(
                    PaymentCommand.RequestPgPayment(
                        paymentId = 999L,
                        cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
                    ),
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @Test
        @DisplayName("0원 결제 시 PG 호출 없이 즉시 완료된다")
        fun `completes immediately for zero amount payment`() {
            // given
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
                PaymentCommand.RequestPgPayment(paymentId = payment.id, cardInfo = null),
            )

            // then
            assertThat(result).isInstanceOf(PgPaymentResult.NotRequired::class.java)
        }
    }

    @Nested
    @DisplayName("processCallback")
    inner class ProcessCallback {

        @Test
        @DisplayName("SUCCESS 콜백을 받으면 PAID로 전이된다")
        fun `transitions to PAID on SUCCESS callback`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_success")
            stubPgTransactionQuery("tx_callback_success", payment.id, "SUCCESS")

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_success",
            )

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Paid::class.java)
        }

        @Test
        @DisplayName("FAILED 콜백을 받으면 FAILED로 전이된다")
        fun `transitions to FAILED on FAILED callback`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_failed")
            stubPgTransactionQuery("tx_callback_failed", payment.id, "FAILED", "잔액 부족")

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_failed",
            )

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Failed::class.java)
        }

        @Test
        @DisplayName("PENDING 트랜잭션이면 StillInProgress를 반환한다")
        fun `returns StillInProgress when still PENDING`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_pending")
            stubPgTransactionQuery("tx_callback_pending", payment.id, "PENDING")

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_pending",
            )

            // then
            assertThat(result).isInstanceOf(ConfirmResult.StillInProgress::class.java)
        }

        @Test
        @DisplayName("이미 PAID 상태인 결제에 콜백이 오면 Paid를 반환한다")
        fun `returns Paid for already paid payment`() {
            // given
            val payment = createPaidPayment()
            stubPgTransactionQuery("tx_paid", payment.id, "SUCCESS")

            // when
            val result = paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_paid",
            )

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Paid::class.java)
        }

        @Test
        @DisplayName("존재하지 않는 주문 ID로 콜백이 오면 예외가 발생한다")
        fun `throws exception when order not found`() {
            // when
            val exception = assertThrows<CoreException> {
                paymentService.processCallback(orderId = 999L, externalPaymentKey = "tx_not_found")
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("processInProgressPayment")
    inner class ProcessInProgressPayment {

        @Test
        @DisplayName("SUCCESS 트랜잭션을 찾으면 PAID로 전이된다")
        fun `transitions to PAID when finding SUCCESS transaction`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_scheduler_success")
            stubPgTransactionQuery("tx_scheduler_success", payment.id, "SUCCESS")

            // when
            val result = paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Paid::class.java)
        }

        @Test
        @DisplayName("FAILED 트랜잭션을 찾으면 FAILED로 전이된다")
        fun `transitions to FAILED when finding FAILED transaction`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_scheduler_failed")
            stubPgTransactionQuery("tx_scheduler_failed", payment.id, "FAILED", "카드 한도 초과")

            // when
            val result = paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            assertThat(result).isInstanceOf(ConfirmResult.Failed::class.java)
        }

        @Test
        @DisplayName("externalPaymentKey가 없으면 orderId로 트랜잭션 목록을 조회한다")
        fun `queries by orderId when externalPaymentKey is null`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = null)
            stubPgTransactionListQuery(
                orderId = payment.id.toString().padStart(6, '0'),
                transactions = listOf(TransactionSummary("tx_found", "SUCCESS", null)),
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
        }

        @Test
        @DisplayName("PENDING 트랜잭션이면 StillInProgress를 반환한다")
        fun `returns StillInProgress when still PENDING`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_scheduler_pending")
            stubPgTransactionQuery("tx_scheduler_pending", payment.id, "PENDING")

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
    // WireMock 스텁 헬퍼
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
                                "data": {"transactionKey": "$transactionKey", "status": "PENDING", "reason": null}
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
                .willReturn(aResponse().withStatus(500).withBody("""{"error": "Internal Server Error"}""")),
        )
    }

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

    private fun stubPgTransactionListQuery(orderId: String, transactions: List<TransactionSummary>) {
        val transactionsJson = transactions.joinToString(",") { tx ->
            """{"transactionKey": "${tx.transactionKey}", "status": "${tx.status}", "reason": ${tx.reason?.let { "\"$it\"" } ?: "null"}}"""
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
                                "data": {"orderId": "$orderId", "transactions": [$transactionsJson]}
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    // ===========================================
    // 도메인 픽스처 헬퍼
    // ===========================================

    private fun createOrder(totalAmount: Money = Money.krw(10000)): Order {
        val order = Order.place(1L)
        order.addOrderItem(productId = 1L, quantity = 1, productName = "테스트 상품", unitPrice = totalAmount)
        return orderRepository.save(order)
    }

    private fun createPendingPayment(): Payment {
        val order = createOrder()
        return paymentRepository.save(
            Payment.create(
                userId = 1L,
                orderId = order.id,
                totalAmount = order.totalAmount,
                usedPoint = Money.krw(5000),
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
                cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
            ),
        )
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
