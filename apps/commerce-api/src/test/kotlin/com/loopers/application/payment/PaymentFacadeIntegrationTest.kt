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
import com.loopers.domain.payment.PaymentCommand
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
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
 * - 오케스트레이션: 포인트/쿠폰 결합 결제 흐름
 * - 외부 API 연동: PG 예외 처리
 */
@SpringBootTest
@DisplayName("PaymentFacade 통합 테스트")
class PaymentFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
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
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @MockkBean
    private lateinit var pgClient: PgClient

    @BeforeEach
    fun setup() {
        every {
            pgClient.requestPayment(match { it.amount == Money.ZERO_KRW })
        } returns PgPaymentCreateResult.NotRequired
    }

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
                { assertThat(payment.paidAmount).isEqualTo(Money.ZERO_KRW) },
                { assertThat(payment.usedPoint).isEqualTo(Money.krw(10000)) },
                { assertThat(payment.couponDiscount).isEqualTo(Money.krw(5000)) },
                { assertThat(payment.status).isEqualTo(PaymentStatus.PAID) },
            )

            // 쿠폰이 사용됨
            val usedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
            assertThat(usedCoupon.status).isEqualTo(UsageStatus.USED)
        }
    }

    @Nested
    @DisplayName("handleCallback - 결제 콜백 처리")
    inner class HandleCallback {

        @Test
        @DisplayName("잘못된 externalPaymentKey로 조회 시 PG에서 예외가 발생한다")
        fun `callback with invalid externalPaymentKey throws exception from PG`() {
            // given
            val payment = createInProgressPayment()
            val wrongKey = "wrong_key"
            val criteria = PaymentCriteria.ProcessCallback(
                orderId = payment.orderId,
                externalPaymentKey = wrongKey,
            )

            // PG에서 wrong_key로 조회 시 예외 발생
            every { pgClient.findTransaction(wrongKey) } throws RuntimeException("Transaction not found")

            // when & then
            assertThrows<RuntimeException> {
                paymentFacade.processCallback(criteria)
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
        val payment = paymentService.create(
            PaymentCommand.Create(
                userId = userId,
                orderId = savedOrder.id,
                totalAmount = savedOrder.totalAmount,
                usedPoint = Money.krw(5000),
                issuedCouponId = null,
                couponDiscount = Money.ZERO_KRW,
            ),
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
