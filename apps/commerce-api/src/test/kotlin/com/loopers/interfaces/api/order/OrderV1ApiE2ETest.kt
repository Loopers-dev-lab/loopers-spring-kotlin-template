package com.loopers.interfaces.api.order

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.loopers.domain.payment.CardType
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
@DisplayName("OrderV1Api E2E 테스트")
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
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
    @DisplayName("POST /api/v1/orders")
    inner class PlaceOrder {

        @Test
        @DisplayName("주문을 생성하면 200 OK와 주문 ID를 반환한다")
        fun returnOrderId_whenOrderIsPlaced() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000))
            createPointAccount(userId, Money.krw(50000))
            stubPgPaymentSuccess()

            val request = OrderV1Request.PlaceOrder(
                items = listOf(OrderV1Request.PlaceOrderItem(productId = product.id, quantity = 2)),
                usePoint = 30000,
                cardType = CardType.HYUNDAI,
                cardNo = "1234-5678-9012-3456",
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.orderId).isNotNull()
        }

        @Test
        @DisplayName("포인트가 부족하면 400 Bad Request를 반환한다")
        fun returnBadRequest_whenInsufficientPoints() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000))
            createPointAccount(userId, Money.krw(5000))

            val request = OrderV1Request.PlaceOrder(
                items = listOf(OrderV1Request.PlaceOrderItem(productId = product.id, quantity = 1)),
                usePoint = 10000,
                cardType = CardType.HYUNDAI,
                cardNo = "1234-5678-9012-3456",
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        @DisplayName("재고가 부족하면 400 Bad Request를 반환한다")
        fun returnBadRequest_whenInsufficientStock() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000), stockQuantity = 5)
            createPointAccount(userId, Money.krw(100000))

            val request = OrderV1Request.PlaceOrder(
                items = listOf(OrderV1Request.PlaceOrderItem(productId = product.id, quantity = 10)),
                usePoint = 100000,
                cardType = CardType.HYUNDAI,
                cardNo = "1234-5678-9012-3456",
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        @DisplayName("존재하지 않는 상품을 주문하면 404 Not Found를 반환한다")
        fun returnNotFound_whenProductDoesNotExist() {
            // given
            val userId = 1L
            createPointAccount(userId, Money.krw(100000))

            val request = OrderV1Request.PlaceOrder(
                items = listOf(OrderV1Request.PlaceOrderItem(productId = 999L, quantity = 1)),
                usePoint = 10000,
                cardType = CardType.HYUNDAI,
                cardNo = "1234-5678-9012-3456",
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @Test
        @DisplayName("X-USER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        fun returnBadRequest_whenUserIdHeaderIsMissing() {
            // given
            val product = createProduct()
            val request = OrderV1Request.PlaceOrder(
                items = listOf(OrderV1Request.PlaceOrderItem(productId = product.id, quantity = 1)),
                usePoint = 10000,
                cardType = CardType.HYUNDAI,
                cardNo = "1234-5678-9012-3456",
            )

            // when
            val response = placeOrder(null, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    private fun stubPgPaymentSuccess() {
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
                                "data": {"transactionKey": "tx_test_${System.currentTimeMillis()}", "status": "PENDING"}
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    private fun createProduct(
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(
            name = "테스트 상품",
            price = price,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }

    private fun createPointAccount(userId: Long, balance: Money): PointAccount {
        return pointAccountRepository.save(PointAccount.of(userId, balance))
    }

    private fun placeOrder(
        userId: Long?,
        request: OrderV1Request.PlaceOrder,
    ): ResponseEntity<ApiResponse<OrderV1Response.PlaceOrder>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            userId?.let { set("X-USER-ID", it.toString()) }
        }

        return testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(request, headers),
            object : ParameterizedTypeReference<ApiResponse<OrderV1Response.PlaceOrder>>() {},
        )
    }
}
