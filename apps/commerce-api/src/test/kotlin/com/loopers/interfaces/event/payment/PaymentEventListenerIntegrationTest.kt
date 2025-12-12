package com.loopers.interfaces.event.payment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentCreatedEventV1
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.TimeUnit

/**
 * PaymentEventListener E2E 통합 테스트
 *
 * 검증 범위:
 * - PaymentCreatedEventV1 → PG 결제 요청 (AFTER_COMMIT, 비동기)
 */
@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
@DisplayName("PaymentEventListener 통합 테스트")
class PaymentEventListenerIntegrationTest @Autowired constructor(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
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
    @DisplayName("onPaymentCreated")
    inner class OnPaymentCreated {

        @Test
        @DisplayName("PaymentCreatedEventV1 발행 시 PG 결제 요청이 수행된다")
        fun `PaymentCreatedEventV1 triggers PG payment request`() {
            // given
            val payment = transactionTemplate.execute { createPendingPayment() }!!
            stubPgPaymentSuccess("tx_test_123")

            val event = PaymentCreatedEventV1(paymentId = payment.id)

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용하여 WireMock 호출 검증
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                verify(postRequestedFor(urlEqualTo("/api/v1/payments")))
            }
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
        val product = createProduct(price = totalAmount)
        val order = Order.place(1L)
        order.addOrderItem(productId = product.id, quantity = 1, productName = "테스트 상품", unitPrice = totalAmount)
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
}
