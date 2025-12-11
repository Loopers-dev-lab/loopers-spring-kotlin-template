package com.loopers.application.order

import com.fasterxml.jackson.core.type.TypeReference
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.loopers.application.product.ProductCacheKeys
import com.loopers.cache.CacheTemplate
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountAmount
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.UsageStatus
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.ProductView
import com.loopers.domain.product.Stock
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.TestPropertySource
import java.time.ZonedDateTime

/**
 * OrderFacade 통합 테스트
 *
 * 검증 범위:
 * - 주문 생성 시 리소스 할당 (재고 감소, 포인트 차감, 쿠폰 사용)
 * - PG 결제 요청 및 결과 처리
 * - PG 실패 시 리소스 복구
 * - 트랜잭션 원자성 (실패 시 모든 변경사항 롤백)
 * - 캐시 일관성 (주문 성공 시 상품 캐시 갱신)
 */
@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = ["pg.base-url=http://localhost:\${wiremock.server.port}"])
@DisplayName("OrderFacade 통합 테스트")
class OrderFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val cacheTemplate: CacheTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private val FIXED_TIME: ZonedDateTime = ZonedDateTime.parse("2025-01-15T10:00:00+09:00[Asia/Seoul]")
    }

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
    @DisplayName("placeOrder")
    inner class PlaceOrder {

        @Test
        @DisplayName("주문 생성 시 재고가 감소하고 포인트가 차감된다")
        fun `decreases stock and deducts point when order is placed`() {
            // given
            val userId = 1L
            val product1 = createProduct(price = Money.krw(15000), stock = Stock.of(100))
            val product2 = createProduct(price = Money.krw(10000), stock = Stock.of(50))
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentSuccess()

            // totalAmount = 15000*2 + 10000*1 = 40000
            // paidAmount = 40000 - 30000 = 10000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(30000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product1.id, quantity = 2),
                    OrderCriteria.PlaceOrderItem(productId = product2.id, quantity = 1),
                ),
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val updatedProduct1 = productRepository.findById(product1.id)!!
            val updatedProduct2 = productRepository.findById(product2.id)!!
            val updatedPointAccount = pointAccountRepository.findByUserId(userId)!!

            assertThat(orderInfo.orderId).isGreaterThan(0)
            assertThat(updatedProduct1.stock.amount).isEqualTo(98)
            assertThat(updatedProduct2.stock.amount).isEqualTo(49)
            assertThat(updatedPointAccount.balance).isEqualTo(Money.krw(70000))
        }

        @Test
        @DisplayName("주문 금액이 올바르게 계산된다")
        fun `calculates total amount correctly`() {
            // given
            val userId = 1L
            val product1 = createProduct(price = Money.krw(10000))
            val product2 = createProduct(price = Money.krw(20000))
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentSuccess()

            // totalAmount = 10000*2 + 20000*1 = 40000
            // paidAmount = 40000 - 10000 = 30000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product1.id, quantity = 2),
                    OrderCriteria.PlaceOrderItem(productId = product2.id, quantity = 1),
                ),
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val savedOrder = orderRepository.findById(orderInfo.orderId)!!
            assertThat(savedOrder.totalAmount).isEqualTo(Money.krw(40000))
        }

        @Test
        @DisplayName("PG 결제 요청이 수락되면 결제가 IN_PROGRESS 상태가 된다")
        fun `payment becomes IN_PROGRESS when PG accepts payment request`() {
            // given
            val userId = 1L
            val productPrice = Money.krw(20000)
            val usePoint = Money.krw(10000)
            val product = createProduct(price = productPrice)
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentSuccess()

            // paidAmount = 20000 - 10000 = 10000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = usePoint,
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val savedOrder = orderRepository.findById(orderInfo.orderId)!!
            val savedPayment = paymentRepository.findByOrderId(orderInfo.orderId)!!

            assertThat(savedOrder.status).isEqualTo(OrderStatus.PLACED)
            assertThat(savedPayment.status).isEqualTo(PaymentStatus.IN_PROGRESS)
            assertThat(savedPayment.paidAmount).isEqualTo(Money.krw(10000))
        }

        @Test
        @DisplayName("주문 성공 시 상품 캐시가 갱신된다")
        fun `updates product cache when order succeeds`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000), stock = Stock.of(100))
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentSuccess()

            // paidAmount = 20000*5 - 50000 = 50000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(50000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 5)),
            )

            // when
            orderFacade.placeOrder(criteria)

            // then
            val cacheKey = ProductCacheKeys.ProductDetail(productId = product.id)
            val cachedValue = cacheTemplate.get(cacheKey, object : TypeReference<ProductView>() {})

            assertThat(cachedValue).isNotNull
            assertThat(cachedValue!!.product.stock.amount).isEqualTo(95)
        }

        @Test
        @DisplayName("0원 결제 시 PG 호출 없이 PAID 상태가 된다")
        fun `payment becomes PAID without PG call for zero amount`() {
            // given
            val userId = 1L
            val productPrice = Money.krw(10000)
            val product = createProduct(price = productPrice)
            createPointAccount(userId, Money.krw(100000))
            // WireMock 스텁 없음 - PG 호출하면 안 됨

            // paidAmount = 10000 - 10000 = 0 → NotRequired → PAID
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = productPrice,
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val savedPayment = paymentRepository.findByOrderId(orderInfo.orderId)!!

            assertThat(savedPayment.status).isEqualTo(PaymentStatus.PAID)
            assertThat(savedPayment.paidAmount).isEqualTo(Money.ZERO_KRW)
        }

        @Test
        @DisplayName("PG 결제 실패 시 재고가 복구된다")
        fun `restores stock when PG payment fails`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000), stock = Stock.of(100))
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentFailure()

            // paidAmount = 20000*5 - 50000 = 50000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(50000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 5)),
            )

            // when
            assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            val restoredProduct = productRepository.findById(product.id)!!
            assertThat(restoredProduct.stock.amount).isEqualTo(100)
        }

        @Test
        @DisplayName("PG 결제 실패 시 포인트가 복구된다")
        fun `restores point when PG payment fails`() {
            // given
            val userId = 1L
            val initialBalance = Money.krw(100000)
            val product = createProduct(price = Money.krw(50000))
            createPointAccount(userId, initialBalance)
            stubPgPaymentFailure()

            // paidAmount = 50000 - 30000 = 20000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(30000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
            )

            // when
            assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            val restoredPointAccount = pointAccountRepository.findByUserId(userId)!!
            assertThat(restoredPointAccount.balance).isEqualTo(initialBalance)
        }

        @Test
        @DisplayName("PG 결제 실패 시 쿠폰이 복구된다")
        fun `restores coupon when PG payment fails`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(30000))
            createPointAccount(userId, Money.krw(100000))

            val coupon = createCoupon(discountType = DiscountType.FIXED_AMOUNT, discountValue = 5000)
            val issuedCoupon = createIssuedCoupon(userId, coupon)

            stubPgPaymentFailure()

            // paidAmount = 30000 - 10000 - 5000 = 15000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            val restoredCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
            assertThat(restoredCoupon.status).isEqualTo(UsageStatus.AVAILABLE)
        }

        @Test
        @DisplayName("PG 결제 실패 시 주문이 취소된다")
        fun `cancels order when PG payment fails`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000))
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentFailure()

            // paidAmount = 20000 - 10000 = 10000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
            )

            // when
            assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            val orders = orderRepository.findAll()
            assertThat(orders).hasSize(1)
            assertThat(orders.first().status).isEqualTo(OrderStatus.CANCELLED)
        }

        @Test
        @DisplayName("PG 결제 실패 시 INTERNAL_ERROR 예외가 발생한다")
        fun `throws INTERNAL_ERROR when PG payment fails`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000))
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentFailure()

            // paidAmount = 20000 - 10000 = 10000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
            )

            // when
            val exception = assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.INTERNAL_ERROR)
        }

        @Test
        @DisplayName("정액 쿠폰 적용 시 할인이 적용된다")
        fun `applies fixed amount coupon discount`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(30000))
            createPointAccount(userId, Money.krw(100000))

            val coupon = createCoupon(discountType = DiscountType.FIXED_AMOUNT, discountValue = 5000)
            val issuedCoupon = createIssuedCoupon(userId, coupon)

            stubPgPaymentSuccess()

            // paidAmount = 30000 - 10000 - 5000 = 15000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findByOrderId(orderInfo.orderId)!!
            val updatedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!

            assertThat(payment.couponDiscount).isEqualTo(Money.krw(5000))
            assertThat(payment.paidAmount).isEqualTo(Money.krw(15000))
            assertThat(payment.issuedCouponId).isEqualTo(issuedCoupon.id)
            assertThat(updatedCoupon.status).isEqualTo(UsageStatus.USED)
        }

        @Test
        @DisplayName("정률 쿠폰 적용 시 할인이 적용된다")
        fun `applies rate coupon discount`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(50000))
            createPointAccount(userId, Money.krw(100000))

            val coupon = createCoupon(discountType = DiscountType.RATE, discountValue = 10)
            val issuedCoupon = createIssuedCoupon(userId, coupon)

            stubPgPaymentSuccess()

            // couponDiscount = 50000 * 10% = 5000
            // paidAmount = 50000 - 20000 - 5000 = 25000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(20000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findByOrderId(orderInfo.orderId)!!
            assertThat(payment.couponDiscount).isEqualTo(Money.krw(5000))
            assertThat(payment.paidAmount).isEqualTo(Money.krw(25000))
        }

        @Test
        @DisplayName("쿠폰 할인이 주문 금액을 초과하면 주문 금액으로 제한된다")
        fun `caps coupon discount at order amount`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(15000))
            createPointAccount(userId, Money.krw(100000))

            // 20000원 쿠폰이지만 주문금액 15000원으로 제한됨
            val coupon = createCoupon(discountType = DiscountType.FIXED_AMOUNT, discountValue = 20000)
            val issuedCoupon = createIssuedCoupon(userId, coupon)

            // couponDiscount = min(20000, 15000) = 15000으로 제한
            // paidAmount = 15000 - 0 - 15000 = 0 → 0원 결제
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.ZERO_KRW,
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findByOrderId(orderInfo.orderId)!!
            assertThat(payment.couponDiscount).isEqualTo(Money.krw(15000))
            assertThat(payment.paidAmount).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.status).isEqualTo(PaymentStatus.PAID)
        }

        @Test
        @DisplayName("쿠폰 없이 주문하면 쿠폰 할인이 적용되지 않는다")
        fun `no coupon discount when ordering without coupon`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000))
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentSuccess()

            // paidAmount = 20000 - 10000 = 10000 > 0 → PG 호출
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                issuedCouponId = null,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findByOrderId(orderInfo.orderId)!!
            assertThat(payment.couponDiscount).isEqualTo(Money.ZERO_KRW)
            assertThat(payment.paidAmount).isEqualTo(Money.krw(10000))
            assertThat(payment.issuedCouponId).isNull()
        }

        @Test
        @DisplayName("재고 부족 시 모든 변경사항이 롤백된다")
        fun `rolls back all changes when stock is insufficient`() {
            // given
            val userId = 1L
            val normalStock = createProduct(stock = Stock.of(100))
            val insufficientStock = createProduct(stock = Stock.of(5))
            createPointAccount(userId, Money.krw(100000))

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(30000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = normalStock.id, quantity = 2),
                    OrderCriteria.PlaceOrderItem(productId = insufficientStock.id, quantity = 10),
                ),
            )

            // when
            val exception = assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            val unchangedNormal = productRepository.findById(normalStock.id)!!
            val unchangedInsufficient = productRepository.findById(insufficientStock.id)!!
            val unchangedPoint = pointAccountRepository.findByUserId(userId)!!

            assertThat(unchangedNormal.stock.amount).isEqualTo(100)
            assertThat(unchangedInsufficient.stock.amount).isEqualTo(5)
            assertThat(unchangedPoint.balance).isEqualTo(Money.krw(100000))
        }

        @Test
        @DisplayName("포인트 부족 시 재고 감소가 롤백된다")
        fun `rolls back stock decrease when point is insufficient`() {
            // given
            val userId = 1L
            val product = createProduct(stock = Stock.of(100))
            createPointAccount(userId, Money.krw(5000))

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
            )

            // when
            val exception = assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            val unchangedProduct = productRepository.findById(product.id)!!
            assertThat(unchangedProduct.stock.amount).isEqualTo(100)
        }

        @Test
        @DisplayName("이미 사용된 쿠폰으로 주문 시 롤백된다")
        fun `rolls back when using already used coupon`() {
            // given
            val userId = 1L
            val product = createProduct(stock = Stock.of(100))
            createPointAccount(userId, Money.krw(100000))

            val coupon = createCoupon()
            val issuedCoupon = createIssuedCoupon(userId, coupon)

            // 쿠폰을 먼저 사용 처리
            issuedCoupon.use(userId, coupon, FIXED_TIME)
            issuedCouponRepository.save(issuedCoupon)

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val exception = assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            val unchangedProduct = productRepository.findById(product.id)!!
            val unchangedPoint = pointAccountRepository.findByUserId(userId)!!

            assertThat(unchangedProduct.stock.amount).isEqualTo(100)
            assertThat(unchangedPoint.balance).isEqualTo(Money.krw(100000))
        }

        @Test
        @DisplayName("존재하지 않는 상품으로 주문 시 예외가 발생한다")
        fun `throws exception when ordering non-existing product`() {
            // given
            val userId = 1L
            createPointAccount(userId, Money.krw(100000))

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = 999L, quantity = 1)),
            )

            // when
            val exception = assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @Test
        @DisplayName("리소스 할당 실패 시 캐시가 갱신되지 않는다")
        fun `does not update cache when resource allocation fails`() {
            // given
            val userId = 1L
            val product = createProduct(stock = Stock.of(5))
            createPointAccount(userId, Money.krw(100000))

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 10)),
            )

            // when
            assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            val cacheKey = ProductCacheKeys.ProductDetail(productId = product.id)
            val cachedValue = cacheTemplate.get(cacheKey, object : TypeReference<ProductView>() {})

            assertThat(cachedValue).isNull()
        }
    }

    // ===========================================
    // WireMock 스텁 헬퍼
    // ===========================================

    private fun stubPgPaymentSuccess() {
        stubFor(
            post(urlEqualTo("/api/v1/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(pgPaymentSuccessResponse()),
                ),
        )
    }

    private fun stubPgPaymentFailure() {
        stubFor(
            post(urlEqualTo("/api/v1/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(pgPaymentFailureResponse()),
                ),
        )
    }

    private fun pgPaymentSuccessResponse() = """
        {
            "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
            "data": {
                "transactionKey": "tx_test_${System.currentTimeMillis()}",
                "status": "PENDING"
            }
        }
    """.trimIndent()

    private fun pgPaymentFailureResponse() = """
        {
            "meta": {"result": "FAIL", "errorCode": "PG_ERROR", "message": "결제 처리 실패"},
            "data": null
        }
    """.trimIndent()

    // ===========================================
    // Criteria 헬퍼
    // ===========================================

    private fun placeOrderCriteria(
        userId: Long = 1L,
        usePoint: Money = Money.krw(10000),
        items: List<OrderCriteria.PlaceOrderItem>,
        issuedCouponId: Long? = null,
        cardType: String? = "HYUNDAI",
        cardNo: String? = "1234-5678-9012-3456",
    ) = OrderCriteria.PlaceOrder(
        userId = userId,
        usePoint = usePoint,
        items = items,
        issuedCouponId = issuedCouponId,
        cardType = cardType,
        cardNo = cardNo,
    )

    // ===========================================
    // 도메인 픽스처 헬퍼
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

    private fun createIssuedCoupon(userId: Long, coupon: Coupon): IssuedCoupon {
        return issuedCouponRepository.save(coupon.issue(userId))
    }
}
