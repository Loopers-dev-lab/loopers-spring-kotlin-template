package com.loopers.integration

import com.loopers.application.order.OrderCriteria
import com.loopers.application.order.OrderFacade
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountAmount
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.UsageStatus
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

/**
 * 결제 경계값 테스트
 * - 포인트 전액 결제 (카드 금액 0원)
 * - 카드 전액 결제 (포인트 0원)
 * - 혼합 결제 (포인트 + 카드)
 * - 쿠폰 할인 적용 결제
 */
@SpringBootTest
@DisplayName("결제 경계값 테스트")
class PaymentBoundaryTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val paymentRepository: PaymentRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Nested
    @DisplayName("포인트 전액 결제 (카드 금액 0원)")
    inner class PointOnlyPayment {

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
    }

    @Nested
    @DisplayName("혼합 결제 (포인트 + 카드)")
    inner class MixedPayment {

        @Test
        @DisplayName("포인트와 카드를 함께 사용하는 경우 requiresCardPayment()가 true를 반환한다")
        fun `mixed payment requires card payment`() {
            // given
            val criteria = OrderCriteria.PlaceOrder(
                userId = 1L,
                usePoint = Money.krw(5000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = 1L, quantity = 1),
                ),
                issuedCouponId = null,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            )

            // when & then
            assertThat(criteria.requiresCardPayment()).isTrue()
        }

        @Test
        @DisplayName("포인트만 사용하는 경우 requiresCardPayment()가 false를 반환한다")
        fun `point only payment does not require card payment`() {
            // given
            val criteria = OrderCriteria.PlaceOrder(
                userId = 1L,
                usePoint = Money.krw(10000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = 1L, quantity = 1),
                ),
                issuedCouponId = null,
                cardType = null,
                cardNo = null,
            )

            // when & then
            assertThat(criteria.requiresCardPayment()).isFalse()
        }
    }

    @Nested
    @DisplayName("쿠폰 할인 적용 결제")
    inner class CouponDiscountPayment {

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
}
