package com.loopers.interfaces.api.order

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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    inner class PlaceOrder {

        @DisplayName("주문을 생성하면 200 OK와 주문 ID를 반환한다")
        @Test
        fun returnOrderId_whenOrderIsPlaced() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(10000), stock = Stock.of(100))
            createPointAccount(userId, Money.krw(50000))

            val request = OrderV1Request.PlaceOrder(
                items = listOf(
                    OrderV1Request.PlaceOrderItem(
                        productId = product.id,
                        quantity = 2,
                    ),
                ),
                usePoint = 20000,
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.orderId).isNotNull() },
            )

            val updatedProduct = productRepository.findById(product.id)!!
            assertThat(updatedProduct.stock.amount).isEqualTo(98)

            val updatedAccount = pointAccountRepository.findByUserId(userId)!!
            assertThat(updatedAccount.balance.amount.toInt()).isEqualTo(30000)
        }

        @DisplayName("여러 상품을 한 번에 주문할 수 있다")
        @Test
        fun returnOrderId_whenOrderingMultipleProducts() {
            // given
            val userId = 1L
            val product1 = createProduct(price = Money.krw(10000), stock = Stock.of(100))
            val product2 = createProduct(price = Money.krw(20000), stock = Stock.of(50))
            createPointAccount(userId, Money.krw(100000))

            val request = OrderV1Request.PlaceOrder(
                items = listOf(
                    OrderV1Request.PlaceOrderItem(productId = product1.id, quantity = 1),
                    OrderV1Request.PlaceOrderItem(productId = product2.id, quantity = 2),
                ),
                usePoint = 50000,
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.orderId).isNotNull() },
            )

            val updatedProduct1 = productRepository.findById(product1.id)!!
            assertThat(updatedProduct1.stock.amount).isEqualTo(99)

            val updatedProduct2 = productRepository.findById(product2.id)!!
            assertThat(updatedProduct2.stock.amount).isEqualTo(48)
        }

        @DisplayName("포인트가 부족하면 400 Bad Request를 반환한다")
        @Test
        fun returnBadRequest_whenInsufficientPoints() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(10000))
            createPointAccount(userId, Money.krw(5000))

            val request = OrderV1Request.PlaceOrder(
                items = listOf(
                    OrderV1Request.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                usePoint = 10000,
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("재고가 부족하면 400 Bad Request를 반환한다")
        @Test
        fun returnBadRequest_whenInsufficientStock() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(10000), stock = Stock.of(5))
            createPointAccount(userId, Money.krw(100000))

            val request = OrderV1Request.PlaceOrder(
                items = listOf(
                    OrderV1Request.PlaceOrderItem(productId = product.id, quantity = 10),
                ),
                usePoint = 100000,
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("존재하지 않는 상품을 주문하면 404 Not Found를 반환한다")
        @Test
        fun returnNotFound_whenProductDoesNotExist() {
            // given
            val userId = 1L
            createPointAccount(userId, Money.krw(100000))

            val request = OrderV1Request.PlaceOrder(
                items = listOf(
                    OrderV1Request.PlaceOrderItem(productId = 999L, quantity = 1),
                ),
                usePoint = 10000,
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("X-USER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        @Test
        fun returnBadRequest_whenUserIdHeaderIsMissing() {
            // given
            val product = createProduct()
            val request = OrderV1Request.PlaceOrder(
                items = listOf(
                    OrderV1Request.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                usePoint = 10000,
            )

            // when
            val response = placeOrder(null, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("사용 포인트가 결제 금액과 일치하지 않으면 400 Bad Request를 반환한다")
        @Test
        fun returnBadRequest_whenUsedPointDoesNotMatchPaymentAmount() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(10000))
            createPointAccount(userId, Money.krw(50000))

            val request = OrderV1Request.PlaceOrder(
                items = listOf(
                    OrderV1Request.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                usePoint = 5000,
            )

            // when
            val response = placeOrder(userId, request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    private fun createBrand(name: String = "테스트 브랜드"): Brand {
        return brandRepository.save(Brand.create(name))
    }

    private fun createProduct(
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stock: Stock = Stock.of(100),
    ): Product {
        val brand = createBrand()
        val product = Product.create(
            name = name,
            price = price,
            stock = stock,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }

    private fun createPointAccount(userId: Long, balance: Money = Money.krw(100000)): PointAccount {
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
