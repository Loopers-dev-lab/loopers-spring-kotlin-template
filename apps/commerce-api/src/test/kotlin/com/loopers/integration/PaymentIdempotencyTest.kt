package com.loopers.integration

import com.loopers.application.order.OrderFacade
import com.loopers.application.payment.PaymentFacade
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
import com.loopers.interfaces.api.payment.PaymentWebhookV1Controller
import com.loopers.interfaces.api.payment.PaymentWebhookV1Request
import com.loopers.support.error.CoreException
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.Instant

/**
 * 결제 멱등성 테스트
 * - 중복 콜백 요청 처리
 * - 이미 처리된 결제에 대한 콜백 처리
 */
@SpringBootTest
@DisplayName("결제 멱등성 테스트")
class PaymentIdempotencyTest @Autowired constructor(
    private val webhookController: PaymentWebhookV1Controller,
    private val paymentFacade: PaymentFacade,
    private val paymentService: PaymentService,
    private val orderFacade: OrderFacade,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @MockBean
    private lateinit var pgClient: PgClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Nested
    @DisplayName("중복 콜백 요청 처리")
    inner class DuplicateCallbackHandling {

        @Test
        @DisplayName("이미 PAID 상태인 결제에 콜백이 오면 SUCCESS를 반환한다")
        fun `duplicate callback for PAID payment returns SUCCESS`() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!
            val transaction = createTransaction(
                transactionKey = externalPaymentKey,
                orderId = payment.orderId,
                amount = payment.paidAmount,
                status = PgTransactionStatus.SUCCESS,
            )

            // 결제 성공 처리 (첫 번째)
            orderFacade.handlePaymentResult(
                paymentId = payment.id,
                transactions = listOf(transaction),
                currentTime = Instant.now(),
            )

            // 동일한 콜백 요청 (두 번째 - 멱등성 테스트)
            val duplicateRequest = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            // when - PgClient는 호출되지 않음 (이미 처리됨)
            val response = webhookController.handleCallback(duplicateRequest)

            // then
            assertThat(response.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

            // 결제 상태는 여전히 PAID
            val unchangedPayment = paymentRepository.findById(payment.id)!!
            assertThat(unchangedPayment.status).isEqualTo(PaymentStatus.PAID)
        }

        @Test
        @DisplayName("이미 FAILED 상태인 결제에 콜백이 오면 SUCCESS를 반환한다")
        fun `callback for FAILED payment returns SUCCESS`() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!

            // 결제 실패 처리
            val failedTransaction = createTransaction(
                transactionKey = externalPaymentKey,
                orderId = payment.orderId,
                amount = payment.paidAmount,
                status = PgTransactionStatus.FAILED,
                failureReason = "테스트 실패",
            )
            orderFacade.handlePaymentResult(
                paymentId = payment.id,
                transactions = listOf(failedTransaction),
                currentTime = Instant.now(),
            )

            // 콜백 요청
            val callbackRequest = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            // when
            val response = webhookController.handleCallback(callbackRequest)

            // then
            assertThat(response.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

            // 결제 상태는 여전히 FAILED
            val unchangedPayment = paymentRepository.findById(payment.id)!!
            assertThat(unchangedPayment.status).isEqualTo(PaymentStatus.FAILED)
        }
    }

    @Nested
    @DisplayName("여러 번 콜백 처리")
    inner class MultipleCallbackProcessing {

        @Test
        @DisplayName("동일한 결제에 대해 여러 번 콜백이 와도 한 번만 처리된다")
        fun `multiple callbacks only process once`() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!

            // PgClient Mock 설정 - externalPaymentKey로 조회 시 SUCCESS 트랜잭션 반환
            val successTransaction = createTransaction(
                transactionKey = externalPaymentKey,
                orderId = payment.orderId,
                amount = payment.paidAmount,
                status = PgTransactionStatus.SUCCESS,
            )
            whenever(pgClient.findTransaction(externalPaymentKey))
                .thenReturn(successTransaction)

            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            // when - 첫 번째 콜백 (실제 처리)
            val response1 = webhookController.handleCallback(request)

            // then
            assertThat(response1.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

            val paidPayment = paymentRepository.findById(payment.id)!!
            assertThat(paidPayment.status).isEqualTo(PaymentStatus.PAID)

            // when - 두 번째 콜백 (멱등성 - PgClient 호출 없이 바로 반환)
            val response2 = webhookController.handleCallback(request)

            // then
            assertThat(response2.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)

            // when - 세 번째 콜백 (멱등성)
            val response3 = webhookController.handleCallback(request)

            // then
            assertThat(response3.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
        }
    }

    @Nested
    @DisplayName("존재하지 않는 결제 콜백 처리")
    inner class NonExistentPaymentCallback {

        @Test
        @DisplayName("존재하지 않는 orderId로 콜백이 오면 예외가 발생한다")
        fun `callback with non-existent orderId throws exception`() {
            // given
            val request = PaymentWebhookV1Request.Callback(
                orderId = 999999L,
                externalPaymentKey = "non_existent_tx_key",
            )

            // when & then
            assertThrows<CoreException> {
                webhookController.handleCallback(request)
            }
        }
    }

    @Nested
    @DisplayName("externalPaymentKey 검증")
    inner class ExternalPaymentKeyValidation {

        @Test
        @DisplayName("잘못된 externalPaymentKey로 조회 시 PG에서 예외가 발생한다")
        fun `callback with invalid externalPaymentKey throws exception from PG`() {
            // given
            val payment = createInProgressPayment()
            val wrongKey = "wrong_key"
            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = wrongKey,
            )

            // PG에서 wrong_key로 조회 시 예외 발생
            whenever(pgClient.findTransaction(wrongKey))
                .thenThrow(RuntimeException("Transaction not found"))

            // when & then
            assertThrows<RuntimeException> {
                webhookController.handleCallback(request)
            }

            // 결제 상태 변경 없음
            val unchangedPayment = paymentRepository.findById(payment.id)!!
            assertThat(unchangedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }
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

    private fun createPointAccount(
        userId: Long = 1L,
        balance: Money = Money.ZERO_KRW,
    ): PointAccount {
        val account = PointAccount.of(userId, balance)
        return pointAccountRepository.save(account)
    }

    private fun createInProgressPayment(
        userId: Long = 1L,
    ): Payment {
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

        // Payment 생성 (PENDING -> IN_PROGRESS)
        val payment = paymentService.createPending(
            userId = userId,
            order = savedOrder,
            usedPoint = Money.krw(5000),
        )

        // initiate로 IN_PROGRESS 전이 + externalPaymentKey 설정
        return paymentService.initiatePayment(
            paymentId = payment.id,
            result = PgPaymentCreateResult.Accepted("tx_test_${payment.id}"),
            attemptedAt = Instant.now(),
        )
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
}
