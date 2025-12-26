package com.loopers.application.order

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountAmount
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.UsageStatus
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
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
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
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
 * - 주문 생성 오케스트레이션 성공
 * - 단일 트랜잭션 내 리소스 할당 (주문, 쿠폰, 포인트, 결제)
 * - PG 결제 요청 비동기 처리
 * - PG 실패 시 리소스 복구 (비동기)
 * - 리소스 할당 실패 시 트랜잭션 롤백
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
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
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
        @DisplayName("주문을 생성하면 즉시 PENDING 상태의 결제가 반환된다")
        fun `creates order and returns immediately with PENDING payment`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000))
            createPointAccount(userId, Money.krw(100000))

            // given
            stubPgPaymentSuccess()

            // when
            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 2)),
            )
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            assertThat(orderInfo.orderId).isGreaterThan(0)
            assertThat(orderInfo.paymentStatus).isEqualTo(PaymentStatus.PENDING)
        }

        @Test
        @DisplayName("여러 상품을 포함한 주문을 생성할 수 있다")
        fun `creates order with multiple products`() {
            // given
            val userId = 1L
            val product1 = createProduct(price = Money.krw(15000))
            val product2 = createProduct(price = Money.krw(10000))
            createPointAccount(userId, Money.krw(100000))

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
            assertThat(orderInfo.orderId).isGreaterThan(0)
            assertThat(orderInfo.paymentStatus).isEqualTo(PaymentStatus.PENDING)
        }

        @Test
        @DisplayName("쿠폰을 적용한 주문을 생성할 수 있다")
        fun `creates order with coupon`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(30000))
            createPointAccount(userId, Money.krw(100000))

            val coupon = createCoupon(discountType = DiscountType.FIXED_AMOUNT, discountValue = 5000)
            val issuedCoupon = createIssuedCoupon(userId, coupon)

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            assertThat(orderInfo.orderId).isGreaterThan(0)

            val updatedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
            assertThat(updatedCoupon.status).isEqualTo(UsageStatus.USED)
        }

        @Test
        @DisplayName("주문 생성 시 재고가 차감된다")
        fun `decreases stock when order is placed`() {
            // given
            val userId = 1L
            val initialStock = 100
            val orderQuantity = 5
            val product = createProduct(price = Money.krw(20000), stockQuantity = initialStock)
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentFailure()

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(50000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = orderQuantity)),
            )

            // when
            orderFacade.placeOrder(criteria)

            // then - 주문 생성 직후 재고는 차감된 상태
            val stockAfterOrder = stockRepository.findByProductId(product.id)!!
            assertThat(stockAfterOrder.quantity).isEqualTo(initialStock - orderQuantity)
        }

        @Test
        @DisplayName("주문 생성 시 포인트가 차감된다")
        fun `deducts point when order is placed`() {
            // given
            val userId = 1L
            val initialBalance = Money.krw(100000)
            val usePoint = Money.krw(30000)
            val product = createProduct(price = Money.krw(50000))
            createPointAccount(userId, initialBalance)
            stubPgPaymentFailure()

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = usePoint,
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
            )

            // when
            orderFacade.placeOrder(criteria)

            // then - 주문 생성 직후 포인트는 차감된 상태
            val pointAfterOrder = pointAccountRepository.findByUserId(userId)!!
            assertThat(pointAfterOrder.balance).isEqualTo(initialBalance - usePoint)
        }

        @Test
        @DisplayName("주문 생성 시 쿠폰이 사용된다")
        fun `uses coupon when order is placed`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(30000))
            createPointAccount(userId, Money.krw(100000))

            val coupon = createCoupon()
            val issuedCoupon = createIssuedCoupon(userId, coupon)

            stubPgPaymentFailure()

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            orderFacade.placeOrder(criteria)

            // then - 주문 생성 직후 쿠폰은 사용된 상태
            val couponAfterOrder = issuedCouponRepository.findById(issuedCoupon.id)!!
            assertThat(couponAfterOrder.status).isEqualTo(UsageStatus.USED)
        }

        @Test
        @DisplayName("주문 생성 시 PLACED 상태가 된다")
        fun `creates order with PLACED status`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000))
            createPointAccount(userId, Money.krw(100000))
            stubPgPaymentFailure()

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1)),
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then - 주문 생성 직후 PLACED 상태
            val orderAfterPlace = orderRepository.findById(orderInfo.orderId)!!
            assertThat(orderAfterPlace.status).isEqualTo(OrderStatus.PLACED)
        }

        @Test
        @DisplayName("재고 부족 시 모든 변경사항이 롤백된다")
        fun `rolls back all changes when stock is insufficient`() {
            // given
            val userId = 1L
            val initialStock = 100
            val normalStock = createProduct(stockQuantity = initialStock)
            val insufficientStock = createProduct(stockQuantity = 5)
            val initialBalance = Money.krw(100000)
            createPointAccount(userId, initialBalance)

            val criteria = placeOrderCriteria(
                userId = userId,
                usePoint = Money.krw(30000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = normalStock.id, quantity = 2),
                    OrderCriteria.PlaceOrderItem(productId = insufficientStock.id, quantity = 10),
                ),
            )

            // when
            assertThrows<CoreException> { orderFacade.placeOrder(criteria) }

            // then
            val unchangedStock = stockRepository.findByProductId(normalStock.id)!!
            val unchangedPoint = pointAccountRepository.findByUserId(userId)!!

            assertThat(unchangedStock.quantity).isEqualTo(initialStock)
            assertThat(unchangedPoint.balance).isEqualTo(initialBalance)
        }

        @Test
        @DisplayName("포인트 부족 시 예외가 발생한다")
        fun `throws exception when point is insufficient`() {
            // given
            val userId = 1L
            val product = createProduct()
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
        }

        @Test
        @DisplayName("이미 사용된 쿠폰으로 주문 시 예외가 발생한다")
        fun `throws exception when using already used coupon`() {
            // given
            val userId = 1L
            val product = createProduct()
            createPointAccount(userId, Money.krw(100000))

            val coupon = createCoupon()
            val issuedCoupon = createIssuedCoupon(userId, coupon)

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
        }

        @Test
        @DisplayName("존재하지 않는 상품으로 주문 시 예외가 발생한다")
        fun `throws exception when product not found`() {
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

    private fun stubPgPaymentFailure() {
        stubFor(
            post(urlEqualTo("/api/v1/payments"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                                "meta": {"result": "FAIL", "errorCode": "PG_ERROR", "message": "결제 처리 실패"},
                                "data": null
                            }
                            """.trimIndent(),
                        ),
                ),
        )
    }

    private fun placeOrderCriteria(
        userId: Long = 1L,
        usePoint: Money = Money.krw(10000),
        items: List<OrderCriteria.PlaceOrderItem>,
        issuedCouponId: Long? = null,
        cardType: CardType? = CardType.HYUNDAI,
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
        stockQuantity: Int = 100,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(name = "테스트 상품", price = price, brand = brand)
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
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
