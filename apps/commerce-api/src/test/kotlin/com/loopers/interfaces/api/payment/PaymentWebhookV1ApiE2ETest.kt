package com.loopers.interfaces.api.payment

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCreateResult
import com.loopers.domain.payment.PgTransaction
import com.loopers.domain.payment.PgTransactionStatus
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
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
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
import java.time.Instant

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("POST /api/v1/payments/callback E2E 테스트")
class PaymentWebhookV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @MockkBean
    private lateinit var pgClient: PgClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Nested
    @DisplayName("결제 콜백 성공 케이스")
    inner class SuccessCallback {

        @Test
        @DisplayName("PG 결제 성공 콜백을 받으면 결제가 PAID 상태가 되고 200 OK를 반환한다")
        fun returnOk_whenPaymentSucceeds() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!

            val successTransaction = createTransaction(
                transactionKey = externalPaymentKey,
                orderId = payment.orderId,
                amount = payment.paidAmount,
                status = PgTransactionStatus.SUCCESS,
            )
            every { pgClient.findTransaction(externalPaymentKey) } returns successTransaction

            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            // when
            val response = sendCallback(request)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )

            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.PAID)
        }
    }

    @Nested
    @DisplayName("결제 콜백 실패 케이스")
    inner class FailedCallback {

        @Test
        @DisplayName("PG 결제 실패 콜백을 받으면 결제가 FAILED 상태가 되고 200 OK를 반환한다")
        fun returnOk_whenPaymentFails() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!

            val failedTransaction = createTransaction(
                transactionKey = externalPaymentKey,
                orderId = payment.orderId,
                amount = payment.paidAmount,
                status = PgTransactionStatus.FAILED,
                failureReason = "잔액 부족",
            )
            every { pgClient.findTransaction(externalPaymentKey) } returns failedTransaction

            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            // when
            val response = sendCallback(request)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )

            val updatedPayment = paymentRepository.findById(payment.id)!!
            assertThat(updatedPayment.status).isEqualTo(PaymentStatus.FAILED)
        }
    }

    @Nested
    @DisplayName("멱등성 처리")
    inner class IdempotencyHandling {

        @Test
        @DisplayName("이미 PAID 상태인 결제에 콜백이 와도 200 OK를 반환한다")
        fun returnOk_whenPaymentAlreadyPaid() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!

            val successTransaction = createTransaction(
                transactionKey = externalPaymentKey,
                orderId = payment.orderId,
                amount = payment.paidAmount,
                status = PgTransactionStatus.SUCCESS,
            )
            every { pgClient.findTransaction(externalPaymentKey) } returns successTransaction

            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            // 첫 번째 콜백 (결제 처리)
            sendCallback(request)

            // when - 두 번째 콜백 (멱등성 테스트)
            val response = sendCallback(request)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )

            val unchangedPayment = paymentRepository.findById(payment.id)!!
            assertThat(unchangedPayment.status).isEqualTo(PaymentStatus.PAID)
        }
    }

    @Nested
    @DisplayName("에러 케이스")
    inner class ErrorCases {

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

            every { pgClient.findTransaction(wrongKey) } throws RuntimeException("Transaction not found")

            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = wrongKey,
            )

            // when
            val response = sendCallback(request)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)

            // 결제 상태는 변경되지 않음
            val unchangedPayment = paymentRepository.findById(payment.id)!!
            assertThat(unchangedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
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

    private fun createPointAccount(
        userId: Long = 1L,
        balance: Money = Money.ZERO_KRW,
    ): PointAccount {
        return pointAccountRepository.save(PointAccount.of(userId, balance))
    }

    private fun createInProgressPayment(userId: Long = 1L): Payment {
        val product = createProduct(price = Money.krw(10000), stock = Stock.of(100))
        createPointAccount(userId = userId, balance = Money.krw(100000))

        // Order 생성
        val order = Order.place(userId)
        order.addOrderItem(
            productId = product.id,
            quantity = 1,
            productName = "테스트 상품",
            unitPrice = Money.krw(10000),
        )
        val savedOrder = orderRepository.save(order)

        // Payment 생성 (PENDING)
        val payment = paymentService.createPending(
            userId = userId,
            order = savedOrder,
            usedPoint = Money.krw(5000),
        )

        // initiate로 IN_PROGRESS 전이 + externalPaymentKey 설정
        payment.initiate(PgPaymentCreateResult.Accepted("tx_test_${payment.id}"), Instant.now())
        return paymentRepository.save(payment)
    }

    private fun createTransaction(
        transactionKey: String,
        orderId: Long,
        amount: Money,
        status: PgTransactionStatus,
        failureReason: String? = null,
    ): PgTransaction {
        return PgTransaction(
            transactionKey = transactionKey,
            orderId = orderId,
            cardType = CardType.KB,
            cardNo = "0000-0000-0000-0000",
            amount = amount,
            status = status,
            failureReason = failureReason,
        )
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
