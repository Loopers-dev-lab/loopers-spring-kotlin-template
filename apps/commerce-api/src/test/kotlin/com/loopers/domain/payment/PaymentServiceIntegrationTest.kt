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
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
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
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import java.time.Instant

/**
 * PaymentService 통합 테스트
 *
 * 검증 범위:
 * - 결제 생성 및 상태 전이 오케스트레이션
 * - PG API 호출 결과에 따른 상태 전이
 * - 트랜잭션 조회 및 결제 확정 흐름
 * - 도메인 이벤트 발행 (PaymentCreatedEventV1, PaymentPaidEventV1, PaymentFailedEventV1)
 */
@SpringBootTest
@AutoConfigureWireMock(port = 0)
@RecordApplicationEvents
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
@DisplayName("PaymentService 통합 테스트")
class PaymentServiceIntegrationTest @Autowired constructor(
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

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

        @Test
        @DisplayName("결제 생성 시 PaymentCreatedEventV1을 발행한다")
        fun `publishes PaymentCreatedEventV1 when creating payment`() {
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
            val events = applicationEvents.stream(PaymentCreatedEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.paymentId).isEqualTo(payment.id)
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
            paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
                ),
            )

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }

        @Test
        @DisplayName("PG 응답 data가 null이면 FAILED로 전이된다")
        fun `returns Failed when PG response data is null`() {
            // given
            val payment = createPendingPayment()
            stubPgPaymentDataNull()

            // when
            paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
                ),
            )

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED)
        }

        @Test
        @DisplayName("PG 서버 에러 시 FAILED로 전이된다")
        fun `returns Failed when PG server error`() {
            // given
            val payment = createPendingPayment()
            stubPgPaymentServerError()

            // when
            paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
                ),
            )

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED)
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
        @DisplayName("0원 결제 시 PG 호출 없이 즉시 PAID로 전이된다")
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
            paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(paymentId = payment.id, cardInfo = null),
            )

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PAID)
        }

        @Test
        @DisplayName("0원 결제 시 PaymentPaidEventV1을 발행한다")
        fun `publishes PaymentPaidEventV1 for zero amount payment`() {
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
            paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(paymentId = payment.id, cardInfo = null),
            )

            // then
            val events = applicationEvents.stream(PaymentPaidEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.paymentId).isEqualTo(payment.id)
            assertThat(event.orderId).isEqualTo(payment.orderId)
        }

        @Test
        @DisplayName("PG 결제 실패 시 PaymentFailedEventV1을 발행한다")
        fun `publishes PaymentFailedEventV1 when PG fails`() {
            // given
            val payment = createPendingPayment()
            stubPgPaymentServerError()

            // when
            paymentService.requestPgPayment(
                PaymentCommand.RequestPgPayment(
                    paymentId = payment.id,
                    cardInfo = CardInfo(cardType = CardType.KB, cardNo = "1234-5678-9012-3456"),
                ),
            )

            // then
            val events = applicationEvents.stream(PaymentFailedEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.paymentId).isEqualTo(payment.id)
            assertThat(event.orderId).isEqualTo(payment.orderId)
            assertThat(event.userId).isEqualTo(payment.userId)
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
            paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_success",
            )

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PAID)
        }

        @Test
        @DisplayName("FAILED 콜백을 받으면 FAILED로 전이된다")
        fun `transitions to FAILED on FAILED callback`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_failed")
            stubPgTransactionQuery("tx_callback_failed", payment.id, "FAILED", "잔액 부족")

            // when
            paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_failed",
            )

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED)
        }

        @Test
        @DisplayName("PENDING 트랜잭션이면 상태가 IN_PROGRESS로 유지된다")
        fun `stays IN_PROGRESS when still PENDING`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_pending")
            stubPgTransactionQuery("tx_callback_pending", payment.id, "PENDING")

            // when
            paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_pending",
            )

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }

        @Test
        @DisplayName("이미 PAID 상태인 결제에 콜백이 오면 상태가 유지된다 (멱등성)")
        fun `stays PAID for already paid payment (idempotency)`() {
            // given
            val payment = createPaidPayment()
            stubPgTransactionQuery("tx_paid", payment.id, "SUCCESS")

            // when
            paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_paid",
            )

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PAID)
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

        @Test
        @DisplayName("SUCCESS 콜백 시 PaymentPaidEventV1을 발행한다")
        fun `publishes PaymentPaidEventV1 on SUCCESS callback`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_event_success")
            stubPgTransactionQuery("tx_callback_event_success", payment.id, "SUCCESS")

            // when
            paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_event_success",
            )

            // then
            val events = applicationEvents.stream(PaymentPaidEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.paymentId).isEqualTo(payment.id)
            assertThat(event.orderId).isEqualTo(payment.orderId)
        }

        @Test
        @DisplayName("FAILED 콜백 시 PaymentFailedEventV1을 발행한다")
        fun `publishes PaymentFailedEventV1 on FAILED callback`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_callback_event_failed")
            stubPgTransactionQuery("tx_callback_event_failed", payment.id, "FAILED", "잔액 부족")

            // when
            paymentService.processCallback(
                orderId = payment.orderId,
                externalPaymentKey = "tx_callback_event_failed",
            )

            // then
            val events = applicationEvents.stream(PaymentFailedEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.paymentId).isEqualTo(payment.id)
            assertThat(event.orderId).isEqualTo(payment.orderId)
            assertThat(event.userId).isEqualTo(payment.userId)
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
            paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PAID)
        }

        @Test
        @DisplayName("FAILED 트랜잭션을 찾으면 FAILED로 전이된다")
        fun `transitions to FAILED when finding FAILED transaction`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_scheduler_failed")
            stubPgTransactionQuery("tx_scheduler_failed", payment.id, "FAILED", "카드 한도 초과")

            // when
            paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED)
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
            paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PAID)
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
            paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED)
        }

        @Test
        @DisplayName("PENDING 트랜잭션이면 상태가 IN_PROGRESS로 유지된다")
        fun `stays IN_PROGRESS when still PENDING`() {
            // given
            val payment = createInProgressPayment(externalPaymentKey = "tx_scheduler_pending")
            stubPgTransactionQuery("tx_scheduler_pending", payment.id, "PENDING")

            // when
            paymentService.processInProgressPayment(paymentId = payment.id)

            // then
            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
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

    private fun createProduct(
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(name = "테스트 상품", price = price, brand = brand)
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }

    private fun createOrder(totalAmount: Money = Money.krw(10000)): Order {
        // PaymentFailedEventV1 처리 시 재고 복구를 위해 Product/Stock 생성
        val product = createProduct(price = totalAmount)
        val order = Order.place(1L)
        order.addOrderItem(productId = product.id, quantity = 1, productName = "테스트 상품", unitPrice = totalAmount)
        return orderRepository.save(order)
    }

    private fun createPendingPayment(): Payment {
        val order = createOrder()
        // PaymentEventListener가 PaymentFailedEventV1을 처리할 때 포인트 복구가 가능하도록 포인트 계좌 생성
        if (pointAccountRepository.findByUserId(1L) == null) {
            pointAccountRepository.save(PointAccount.of(userId = 1L, balance = Money.krw(10000)))
        }
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
