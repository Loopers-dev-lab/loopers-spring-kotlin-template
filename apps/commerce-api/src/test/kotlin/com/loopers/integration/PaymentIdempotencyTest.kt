package com.loopers.integration

import com.loopers.application.order.OrderFacade
import com.loopers.application.order.PaymentResultHandler
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.interfaces.api.payment.PaymentCallbackRequest
import com.loopers.interfaces.api.payment.PaymentWebhookController
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
import org.springframework.http.HttpStatus

/**
 * 결제 멱등성 테스트
 * - 중복 콜백 요청 처리
 * - 이미 처리된 결제에 대한 콜백 처리
 */
@SpringBootTest
@DisplayName("결제 멱등성 테스트")
class PaymentIdempotencyTest @Autowired constructor(
    private val webhookController: PaymentWebhookController,
    private val paymentService: PaymentService,
    private val paymentResultHandler: PaymentResultHandler,
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
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Nested
    @DisplayName("중복 콜백 요청 처리")
    inner class DuplicateCallbackHandling {

        @Test
        @DisplayName("이미 PAID 상태인 결제에 SUCCESS 콜백이 오면 200 OK와 함께 알림 메시지를 반환한다")
        fun `duplicate SUCCESS callback for PAID payment returns 200 OK`() {
            // given
            val payment = createInProgressPayment()
            val transactionKey = "tx_test_123"

            // 결제 성공 처리 (첫 번째 콜백)
            paymentResultHandler.handlePaymentSuccess(payment.id, transactionKey)

            // 동일한 콜백 요청 (두 번째)
            val duplicateRequest = PaymentCallbackRequest(
                transactionKey = transactionKey,
                orderId = payment.orderId.toString(),
                status = "SUCCESS",
                reason = null,
            )

            // when
            val response = webhookController.handleCallback(duplicateRequest)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.result).isEqualTo("OK") },
                { assertThat(response.body?.message).isEqualTo("이미 처리된 결제입니다") },
            )

            // 결제 상태는 여전히 PAID
            val unchangedPayment = paymentRepository.findById(payment.id)!!
            assertThat(unchangedPayment.status).isEqualTo(PaymentStatus.PAID)
        }

        @Test
        @DisplayName("이미 FAILED 상태인 결제에 콜백이 오면 200 OK와 함께 알림 메시지를 반환한다")
        fun `callback for FAILED payment returns 200 OK`() {
            // given
            val payment = createInProgressPayment()
            // payment에 설정된 externalPaymentKey 사용
            val transactionKey = payment.externalPaymentKey!!

            // 결제 실패 처리
            val order = orderRepository.findById(payment.orderId)!!
            val orderItems = order.orderItems.map {
                PaymentResultHandler.OrderItemInfo(
                    productId = it.productId,
                    quantity = it.quantity,
                )
            }
            paymentResultHandler.handlePaymentFailure(payment.id, "테스트 실패", orderItems)

            // 동일한 콜백 요청
            val callbackRequest = PaymentCallbackRequest(
                transactionKey = transactionKey,
                orderId = payment.orderId.toString(),
                status = "SUCCESS",
                reason = null,
            )

            // when
            val response = webhookController.handleCallback(callbackRequest)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.result).isEqualTo("OK") },
                { assertThat(response.body?.message).isEqualTo("이미 처리된 결제입니다") },
            )

            // 결제 상태는 여전히 FAILED
            val unchangedPayment = paymentRepository.findById(payment.id)!!
            assertThat(unchangedPayment.status).isEqualTo(PaymentStatus.FAILED)
        }
    }

    @Nested
    @DisplayName("여러 번 콜백 처리")
    inner class MultipleCallbackProcessing {

        @Test
        @DisplayName("동일한 결제에 대해 여러 번 SUCCESS 콜백이 와도 한 번만 처리된다")
        fun `multiple SUCCESS callbacks only process once`() {
            // given
            val payment = createInProgressPayment()
            // payment에 설정된 externalPaymentKey 사용
            val transactionKey = payment.externalPaymentKey!!

            // 첫 번째 콜백 요청
            val request1 = PaymentCallbackRequest(
                transactionKey = transactionKey,
                orderId = payment.orderId.toString(),
                status = "SUCCESS",
                reason = null,
            )

            // when - 첫 번째 콜백
            val response1 = webhookController.handleCallback(request1)

            // then
            assertThat(response1.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response1.body?.result).isEqualTo("OK")

            val paidPayment = paymentRepository.findById(payment.id)!!
            assertThat(paidPayment.status).isEqualTo(PaymentStatus.PAID)

            // when - 두 번째 콜백 (중복)
            val response2 = webhookController.handleCallback(request1)

            // then - 이미 처리됨
            assertThat(response2.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response2.body?.message).isEqualTo("이미 처리된 결제입니다")

            // when - 세 번째 콜백 (중복)
            val response3 = webhookController.handleCallback(request1)

            // then - 여전히 이미 처리됨
            assertThat(response3.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response3.body?.message).isEqualTo("이미 처리된 결제입니다")
        }
    }

    @Nested
    @DisplayName("존재하지 않는 결제 콜백 처리")
    inner class NonExistentPaymentCallback {

        @Test
        @DisplayName("존재하지 않는 transactionKey로 콜백이 오면 에러를 반환한다")
        fun `callback with non-existent transactionKey returns error`() {
            // given
            val request = PaymentCallbackRequest(
                transactionKey = "non_existent_tx_key",
                orderId = "999",
                status = "SUCCESS",
                reason = null,
            )

            // when
            val response = webhookController.handleCallback(request)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.result).isEqualTo("ERROR") },
                { assertThat(response.body?.message).isEqualTo("결제를 찾을 수 없습니다") },
            )
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

        // Payment 생성 (PENDING -> IN_PROGRESS), paidAmount = 10000 - 5000 = 5000 자동 계산
        val payment = paymentService.createPending(
            userId = userId,
            order = savedOrder,
            usedPoint = Money.krw(5000),
        )

        val startedPayment = paymentService.startPayment(payment.id)

        // externalPaymentKey 설정
        startedPayment.updateExternalPaymentKey("tx_test_${startedPayment.id}")
        return paymentRepository.save(startedPayment)
    }
}
