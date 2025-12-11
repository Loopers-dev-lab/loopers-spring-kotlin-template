package com.loopers.application.payment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountAmount
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.UsageStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
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
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.time.ZonedDateTime

/**
 * PaymentFacade 통합 테스트
 *
 * 검증 범위:
 * - 결제 성공 → 주문 완료
 * - 결제 실패 → 리소스 복구 (포인트, 쿠폰, 재고)
 * - 멱등성 (중복 콜백 처리)
 */
@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
@DisplayName("PaymentFacade 통합 테스트")
class PaymentFacadeIntegrationTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val paymentService: PaymentService,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
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
    @DisplayName("processCallback")
    inner class ProcessCallback {

        @Test
        @DisplayName("결제 성공 시 주문이 PAID 상태가 된다")
        fun `completes order when payment succeeds`() {
            // given
            val payment = createInProgressPayment()
            stubPgTransactionSuccess(payment)

            // when
            paymentFacade.processCallback(callbackCriteria(payment))

            // then
            val updatedOrder = orderRepository.findById(payment.orderId)!!
            assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAID)
        }

        @Test
        @DisplayName("결제 실패 시 사용한 포인트가 복구된다")
        fun `restores used point when payment fails`() {
            // given
            val userId = 1L
            val initialBalance = Money.krw(10000)
            val usedPoint = Money.krw(5000)

            val pointAccount = createPointAccount(userId, initialBalance)
            val payment = createInProgressPaymentWithPoint(userId, usedPoint)

            pointAccount.deduct(usedPoint)
            pointAccountRepository.save(pointAccount)

            stubPgTransactionFailed(payment, "카드 한도 초과")

            // when
            paymentFacade.processCallback(callbackCriteria(payment))

            // then
            val updatedPointAccount = pointAccountRepository.findByUserId(userId)!!
            assertThat(updatedPointAccount.balance).isEqualTo(initialBalance)
        }

        @Test
        @DisplayName("결제 실패 시 사용한 쿠폰이 복구된다")
        fun `restores used coupon when payment fails`() {
            // given
            val userId = 1L
            val coupon = createCoupon()
            val issuedCoupon = createIssuedCoupon(userId, coupon)

            issuedCoupon.use(userId, coupon, ZonedDateTime.now())
            issuedCouponRepository.save(issuedCoupon)

            val payment = createInProgressPaymentWithCoupon(userId, issuedCoupon.id, Money.krw(5000))
            stubPgTransactionFailed(payment, "잔액 부족")

            // when
            paymentFacade.processCallback(callbackCriteria(payment))

            // then
            val updatedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
            assertThat(updatedCoupon.status).isEqualTo(UsageStatus.AVAILABLE)
        }

        @Test
        @DisplayName("결제 실패 시 차감된 재고가 복구된다")
        fun `restores decreased stock when payment fails`() {
            // given
            val initialStock = 10
            val orderQuantity = 2
            val product = createProduct(stock = Stock.of(initialStock))

            product.decreaseStock(orderQuantity)
            productRepository.save(product)

            val payment = createInProgressPaymentWithProduct(product, orderQuantity)
            stubPgTransactionFailed(payment, "카드 오류")

            // when
            paymentFacade.processCallback(callbackCriteria(payment))

            // then
            val updatedProduct = productRepository.findById(product.id)!!
            assertThat(updatedProduct.stock.amount).isEqualTo(initialStock)
        }

        @Test
        @DisplayName("결제 실패 시 주문이 취소 상태가 된다")
        fun `cancels order when payment fails`() {
            // given
            val payment = createInProgressPayment()
            stubPgTransactionFailed(payment, "결제 거절")

            // when
            paymentFacade.processCallback(callbackCriteria(payment))

            // then
            val updatedOrder = orderRepository.findById(payment.orderId)!!
            assertThat(updatedOrder.status).isEqualTo(OrderStatus.CANCELLED)
        }

        @Test
        @DisplayName("이미 PAID 상태인 결제에 콜백이 와도 정상 처리된다")
        fun `handles duplicate callback for already paid payment`() {
            // given
            val payment = createInProgressPayment()
            stubPgTransactionSuccess(payment)

            paymentFacade.processCallback(callbackCriteria(payment))

            // when - 중복 콜백 (예외 없이 처리되어야 함)
            paymentFacade.processCallback(callbackCriteria(payment))

            // then
            val updatedOrder = orderRepository.findById(payment.orderId)!!
            assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAID)
        }
    }

    @Nested
    @DisplayName("processInProgressPayment")
    inner class ProcessInProgressPayment {

        @Test
        @DisplayName("PG에서 SUCCESS 조회 시 주문이 완료된다")
        fun `completes order when PG returns SUCCESS`() {
            // given
            val payment = createInProgressPayment()
            stubPgTransactionSuccess(payment)

            // when
            paymentFacade.processInProgressPayment(payment.id)

            // then
            val updatedOrder = orderRepository.findById(payment.orderId)!!
            assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAID)
        }

        @Test
        @DisplayName("PG에서 FAILED 조회 시 리소스가 복구된다")
        fun `restores resources when PG returns FAILED`() {
            // given
            val userId = 1L
            val initialBalance = Money.krw(10000)
            val usedPoint = Money.krw(5000)

            val pointAccount = createPointAccount(userId, initialBalance)
            val payment = createInProgressPaymentWithPoint(userId, usedPoint)

            pointAccount.deduct(usedPoint)
            pointAccountRepository.save(pointAccount)

            stubPgTransactionFailed(payment, "결제 실패")

            // when
            paymentFacade.processInProgressPayment(payment.id)

            // then
            val updatedOrder = orderRepository.findById(payment.orderId)!!
            val updatedPointAccount = pointAccountRepository.findByUserId(userId)!!

            assertThat(updatedOrder.status).isEqualTo(OrderStatus.CANCELLED)
            assertThat(updatedPointAccount.balance).isEqualTo(initialBalance)
        }

        @Test
        @DisplayName("PG에서 IN_PROGRESS 조회 시 상태가 유지된다")
        fun `keeps status when PG returns IN_PROGRESS`() {
            // given
            val payment = createInProgressPayment()
            stubPgTransactionInProgress(payment)

            // when
            paymentFacade.processInProgressPayment(payment.id)

            // then
            val updatedOrder = orderRepository.findById(payment.orderId)!!
            assertThat(updatedOrder.status).isEqualTo(OrderStatus.PLACED)
        }
    }

    // ===========================================
    // WireMock 스텁 헬퍼
    // ===========================================

    private fun stubPgTransactionSuccess(payment: Payment) {
        stubPgTransaction(payment, "SUCCESS", null)
    }

    private fun stubPgTransactionFailed(payment: Payment, reason: String) {
        stubPgTransaction(payment, "FAILED", reason)
    }

    private fun stubPgTransactionInProgress(payment: Payment) {
        stubPgTransaction(payment, "PENDING", null)
    }

    private fun stubPgTransaction(payment: Payment, status: String, reason: String?) {
        val transactionKey = payment.externalPaymentKey!!
        stubFor(
            get(urlEqualTo("/api/v1/payments/$transactionKey"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(pgTransactionResponse(payment, status, reason)),
                ),
        )
    }

    private fun pgTransactionResponse(payment: Payment, status: String, reason: String?) = """
        {
            "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
            "data": {
                "transactionKey": "${payment.externalPaymentKey}",
                "orderId": "${payment.id.toString().padStart(6, '0')}",
                "cardType": "HYUNDAI",
                "cardNo": "1234-5678-9012-3456",
                "amount": ${payment.paidAmount.amount.toLong()},
                "status": "$status",
                "reason": ${reason?.let { "\"$it\"" } ?: "null"}
            }
        }
    """.trimIndent()

    private fun callbackCriteria(payment: Payment) = PaymentCriteria.ProcessCallback(
        orderId = payment.orderId,
        externalPaymentKey = payment.externalPaymentKey!!,
    )

    // ===========================================
    // 도메인 픽스처 헬퍼
    // ===========================================

    private fun createProduct(
        price: Money = Money.krw(10000),
        stock: Stock = Stock.of(100),
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(name = "테스트 상품", price = price, stock = stock, brand = brand)
        val savedProduct = productRepository.save(product)
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }

    private fun createPointAccount(userId: Long, balance: Money): PointAccount {
        return pointAccountRepository.save(PointAccount.of(userId, balance))
    }

    private fun createCoupon(
        discountType: DiscountType = DiscountType.FIXED_AMOUNT,
        discountValue: Long = 5000,
    ): Coupon {
        val discountAmount = DiscountAmount(type = discountType, value = discountValue)
        return couponRepository.save(Coupon.of(name = "테스트 쿠폰", discountAmount = discountAmount))
    }

    private fun createIssuedCoupon(userId: Long, coupon: Coupon) =
        issuedCouponRepository.save(coupon.issue(userId))

    private fun createInProgressPayment(userId: Long = 1L): Payment {
        val product = createProduct()
        createPointAccount(userId, Money.krw(100000))

        val order = createOrder(userId, product, quantity = 1)
        return createPayment(
            userId = userId,
            order = order,
            usedPoint = Money.ZERO_KRW,
            issuedCouponId = null,
            couponDiscount = Money.ZERO_KRW,
        )
    }

    private fun createInProgressPaymentWithPoint(userId: Long, usedPoint: Money): Payment {
        val product = createProduct()

        val order = createOrder(userId, product, quantity = 1)
        return createPayment(
            userId = userId,
            order = order,
            usedPoint = usedPoint,
            issuedCouponId = null,
            couponDiscount = Money.ZERO_KRW,
        )
    }

    private fun createInProgressPaymentWithCoupon(
        userId: Long,
        issuedCouponId: Long,
        couponDiscount: Money,
    ): Payment {
        val product = createProduct()
        createPointAccount(userId, Money.krw(100000))

        val order = createOrder(userId, product, quantity = 1)
        return createPayment(
            userId = userId,
            order = order,
            usedPoint = Money.ZERO_KRW,
            issuedCouponId = issuedCouponId,
            couponDiscount = couponDiscount,
        )
    }

    private fun createInProgressPaymentWithProduct(product: Product, quantity: Int): Payment {
        val userId = 1L
        createPointAccount(userId, Money.krw(100000))

        val order = createOrder(userId, product, quantity)
        return createPayment(
            userId = userId,
            order = order,
            usedPoint = Money.ZERO_KRW,
            issuedCouponId = null,
            couponDiscount = Money.ZERO_KRW,
        )
    }

    private fun createOrder(userId: Long, product: Product, quantity: Int): Order {
        val order = Order.place(userId)
        order.addOrderItem(
            productId = product.id,
            quantity = quantity,
            productName = product.name,
            unitPrice = product.price,
        )
        return orderRepository.save(order)
    }

    private fun createPayment(
        userId: Long,
        order: Order,
        usedPoint: Money,
        issuedCouponId: Long?,
        couponDiscount: Money,
    ): Payment {
        val payment = paymentService.create(
            PaymentCommand.Create(
                userId = userId,
                orderId = order.id,
                totalAmount = order.totalAmount,
                usedPoint = usedPoint,
                issuedCouponId = issuedCouponId,
                couponDiscount = couponDiscount,
            ),
        )
        payment.initiate(PgPaymentCreateResult.Accepted("tx_test_${payment.id}"), Instant.now())
        return paymentRepository.save(payment)
    }
}
