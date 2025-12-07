package com.loopers.application.payment

import com.loopers.application.order.OrderCriteria
import com.loopers.application.order.OrderFacade
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountAmount
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.UsageStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
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
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

/**
 * PaymentFacade 통합 테스트
 * - 경계값 테스트: 포인트 전액/혼합/쿠폰 할인 결제
 * - 멱등성 테스트: 중복 콜백 처리
 */
@SpringBootTest
@DisplayName("PaymentFacade 통합 테스트")
class PaymentFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val paymentFacade: PaymentFacade,
    private val paymentService: PaymentService,
    private val webhookController: PaymentWebhookV1Controller,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
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
    @DisplayName("placeOrder")
    inner class PlaceOrder {

        @Test
        @DisplayName("포인트로만 결제하면 paidAmount가 0이고 즉시 PAID 상태가 된다")
        fun `point only payment results in zero paidAmount and immediate PAID status`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(10000))
            createPointAccount(userId = userId, balance = Money.krw(100000))

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = null,
                cardType = null,
                cardNo = null,
            )

            // when
            val result = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findById(result.paymentId)!!

            assertAll(
                { assertThat(result.paymentStatus).isEqualTo(PaymentStatus.PAID) },
                { assertThat(payment.paidAmount).isEqualTo(Money.ZERO_KRW) },
                { assertThat(payment.usedPoint).isEqualTo(Money.krw(10000)) },
                { assertThat(payment.status).isEqualTo(PaymentStatus.PAID) },
            )
        }

        @Test
        @DisplayName("쿠폰 할인 후 포인트로만 결제할 수 있다")
        fun `point only payment with coupon discount`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(15000))
            createPointAccount(userId = userId, balance = Money.krw(100000))
            val coupon = createCoupon(discountType = DiscountType.FIXED_AMOUNT, discountValue = 5000)
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            // 15000 - 5000(쿠폰) = 10000원을 포인트로 결제
            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = issuedCoupon.id,
                cardType = null,
                cardNo = null,
            )

            // when
            val result = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findById(result.paymentId)!!

            assertAll(
                { assertThat(result.paymentStatus).isEqualTo(PaymentStatus.PAID) },
                { assertThat(payment.paidAmount).isEqualTo(Money.ZERO_KRW) },
                { assertThat(payment.usedPoint).isEqualTo(Money.krw(10000)) },
                { assertThat(payment.couponDiscount).isEqualTo(Money.krw(5000)) },
            )

            // 쿠폰이 사용됨
            val usedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
            assertThat(usedCoupon.status).isEqualTo(UsageStatus.USED)
        }

        @Test
        @DisplayName("정률 할인 쿠폰 적용 시 할인 금액이 올바르게 계산된다")
        fun `rate discount coupon calculates correct discount amount`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(10000))
            createPointAccount(userId = userId, balance = Money.krw(100000))

            // 10% 할인 쿠폰
            val coupon = createCoupon(discountType = DiscountType.RATE, discountValue = 10)
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            // 10000 * 10% = 1000원 할인, 9000원을 포인트로 결제
            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(9000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = issuedCoupon.id,
                cardType = null,
                cardNo = null,
            )

            // when
            val result = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findById(result.paymentId)!!

            assertAll(
                { assertThat(payment.couponDiscount).isEqualTo(Money.krw(1000)) },
                { assertThat(payment.usedPoint).isEqualTo(Money.krw(9000)) },
                { assertThat(payment.totalAmount).isEqualTo(Money.krw(10000)) },
            )
        }

        @Test
        @DisplayName("정액 할인 쿠폰 적용 시 할인 금액이 올바르게 계산된다")
        fun `fixed amount coupon calculates correct discount amount`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000))
            createPointAccount(userId = userId, balance = Money.krw(100000))

            // 3000원 정액 할인 쿠폰
            val coupon = createCoupon(discountType = DiscountType.FIXED_AMOUNT, discountValue = 3000)
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            // 20000 - 3000 = 17000원을 포인트로 결제
            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(17000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = issuedCoupon.id,
                cardType = null,
                cardNo = null,
            )

            // when
            val result = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findById(result.paymentId)!!

            assertAll(
                { assertThat(payment.couponDiscount).isEqualTo(Money.krw(3000)) },
                { assertThat(payment.usedPoint).isEqualTo(Money.krw(17000)) },
                { assertThat(payment.totalAmount).isEqualTo(Money.krw(20000)) },
            )
        }
    }

    @Nested
    @DisplayName("processCallback")
    inner class ProcessCallback {

        @Test
        @DisplayName("이미 PAID 상태인 결제에 콜백이 오면 SUCCESS를 반환한다")
        fun `duplicate callback for PAID payment returns SUCCESS`() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!
            val transaction = createTransaction(
                transactionKey = externalPaymentKey,
                paymentId = payment.id,
                status = PgTransactionStatus.SUCCESS,
            )

            // PG 조회 Mock - 첫 번째 콜백 처리용
            every { pgClient.findTransaction(externalPaymentKey) } returns transaction

            // 결제 성공 처리 (첫 번째)
            paymentFacade.processCallback(
                PaymentCriteria.ProcessCallback(
                    orderId = payment.orderId,
                    externalPaymentKey = externalPaymentKey,
                ),
            )

            val paidPayment = paymentRepository.findById(payment.id)!!
            assertThat(paidPayment.status).isEqualTo(PaymentStatus.PAID)

            // 동일한 콜백 요청 (두 번째 - 멱등성 테스트)
            val request = PaymentWebhookV1Request.Callback(
                orderId = payment.orderId,
                externalPaymentKey = externalPaymentKey,
            )

            // when - 두 번째 콜백 (멱등성)
            val response = webhookController.handleCallback(request)

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
                paymentId = payment.id,
                status = PgTransactionStatus.FAILED,
                failureReason = "테스트 실패",
            )

            // PG 조회 Mock
            every { pgClient.findTransaction(externalPaymentKey) } returns failedTransaction

            paymentFacade.processCallback(
                PaymentCriteria.ProcessCallback(
                    orderId = payment.orderId,
                    externalPaymentKey = externalPaymentKey,
                ),
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

        @Test
        @DisplayName("동일한 결제에 대해 여러 번 콜백이 와도 한 번만 처리된다")
        fun `multiple callbacks only process once`() {
            // given
            val payment = createInProgressPayment()
            val externalPaymentKey = payment.externalPaymentKey!!

            // PgClient Mock 설정 - externalPaymentKey로 조회 시 SUCCESS 트랜잭션 반환
            val successTransaction = createTransaction(
                transactionKey = externalPaymentKey,
                paymentId = payment.id,
                status = PgTransactionStatus.SUCCESS,
            )
            every { pgClient.findTransaction(externalPaymentKey) } returns successTransaction

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
            every { pgClient.findTransaction(wrongKey) } throws RuntimeException("Transaction not found")

            // when & then
            assertThrows<RuntimeException> {
                webhookController.handleCallback(request)
            }

            // 결제 상태 변경 없음
            val unchangedPayment = paymentRepository.findById(payment.id)!!
            assertThat(unchangedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
        }
    }

    // ===========================================
    // 헬퍼 메서드
    // ===========================================

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

    private fun createCoupon(
        name: String = "테스트 쿠폰",
        discountType: DiscountType = DiscountType.FIXED_AMOUNT,
        discountValue: Long = 5000,
    ): Coupon {
        val discountAmount = DiscountAmount(
            type = discountType,
            value = discountValue,
        )
        val coupon = Coupon.of(name = name, discountAmount = discountAmount)
        return couponRepository.save(coupon)
    }

    private fun createIssuedCoupon(
        userId: Long,
        coupon: Coupon,
    ): IssuedCoupon {
        val issuedCoupon = coupon.issue(userId)
        return issuedCouponRepository.save(issuedCoupon)
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
        paymentId: Long,
        status: PgTransactionStatus,
        failureReason: String? = null,
    ): PgTransaction {
        return PgTransaction(
            transactionKey = transactionKey,
            paymentId = paymentId,
            status = status,
            failureReason = failureReason,
        )
    }
}
