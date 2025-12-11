package com.loopers.interfaces.api.payment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentCommand
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.TestPropertySource
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
@DisplayName("PaymentWebhookV1Api E2E 테스트")
class PaymentWebhookV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
        reset()
        for (circuitBreaker in circuitBreakerRegistry.allCircuitBreakers) {
            circuitBreaker.reset()
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/callback")
    inner class Callback {

        @Test
        @DisplayName("PG 결제 성공 콜백을 받으면 200 OK를 반환한다")
        fun returnOk_whenPaymentSucceeds() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!

            stubPgTransactionSuccess(externalPaymentKey, payment.id)

            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            // when
            val response = sendCallback(request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }

        @Test
        @DisplayName("PG 결제 실패 콜백을 받으면 200 OK를 반환한다")
        fun returnOk_whenPaymentFails() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!

            stubPgTransactionFailed(externalPaymentKey, payment.id)

            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            // when
            val response = sendCallback(request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }

        @Test
        @DisplayName("이미 PAID 상태인 결제에 콜백이 와도 200 OK를 반환한다")
        fun returnOk_whenPaymentAlreadyPaid() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!

            stubPgTransactionSuccess(externalPaymentKey, payment.id)

            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            sendCallback(request)

            // when
            val response = sendCallback(request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }

        @Test
        @DisplayName("존재하지 않는 orderId로 콜백이 오면 404 Not Found를 반환한다")
        fun returnNotFound_whenOrderIdDoesNotExist() {
            // given
            val request = PaymentWebhookV1Request.Callback(
                orderId = 999999L,
                externalPaymentKey = "non_existent_tx_key",
            )

            // when
            val response = sendCallback(request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @Test
        @DisplayName("PG에서 트랜잭션을 찾을 수 없으면 500 Internal Server Error를 반환한다")
        fun returnError_whenPgTransactionNotFound() {
            // given
            val payment = createInProgressPayment()
            val wrongKey = "wrong_transaction_key"

            stubPgTransactionNotFound(wrongKey)

            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = wrongKey,
            )

            // when
            val response = sendCallback(request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        }
    }

    private fun stubPgTransactionSuccess(transactionKey: String, paymentId: Long) {
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
                                    "orderId": "$paymentId",
                                    "cardType": "HYUNDAI",
                                    "cardNo": "1234-5678-****-3456",
                                    "amount": 5000,
                                    "status": "SUCCESS",
                                    "reason": null
                                }
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    private fun stubPgTransactionFailed(transactionKey: String, paymentId: Long) {
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
                                    "orderId": "$paymentId",
                                    "cardType": "HYUNDAI",
                                    "cardNo": "1234-5678-****-3456",
                                    "amount": 5000,
                                    "status": "FAILED",
                                    "reason": "잔액 부족"
                                }
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    private fun stubPgTransactionNotFound(transactionKey: String) {
        stubFor(
            get(urlEqualTo("/api/v1/payments/$transactionKey"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "meta": {"result": "FAIL", "errorCode": "TX_NOT_FOUND", "message": "Transaction not found"},
                                "data": null
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    private fun createInProgressPayment(userId: Long = 1L): Payment {
        val product = createProduct(price = Money.krw(10000))
        createPointAccount(userId, Money.krw(100000))

        val order = Order.place(userId)
        order.addOrderItem(
            productId = product.id,
            quantity = 1,
            productName = "테스트 상품",
            unitPrice = Money.krw(10000),
        )
        val savedOrder = orderRepository.save(order)

        val payment = paymentService.create(
            PaymentCommand.Create(
                userId = userId,
                orderId = savedOrder.id,
                totalAmount = savedOrder.totalAmount,
                usedPoint = Money.krw(5000),
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            ),
        )

        payment.initiate(PgPaymentCreateResult.Accepted("tx_test_${payment.id}"), Instant.now())
        return paymentRepository.save(payment)
    }

    private fun createProduct(
        price: Money = Money.krw(10000),
        stock: Stock = Stock.of(100),
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(
            name = "테스트 상품",
            price = price,
            stock = stock,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }

    private fun createPointAccount(userId: Long, balance: Money): PointAccount {
        return pointAccountRepository.save(PointAccount.of(userId, balance))
    }

    private fun sendCallback(
        request: PaymentWebhookV1Request.Callback,
    ): ResponseEntity<ApiResponse<Unit>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        return testRestTemplate.exchange(
            "/api/v1/payments/callback",
            HttpMethod.POST,
            HttpEntity(request, headers),
            object : ParameterizedTypeReference<ApiResponse<Unit>>() {},
        )
    }
}
