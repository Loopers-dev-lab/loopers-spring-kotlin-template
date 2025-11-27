package com.loopers.application.order

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountAmount
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.UsageStatus
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.PaymentRepository
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
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
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private val FIXED_TIME = java.time.ZonedDateTime.parse("2025-01-15T10:00:00+09:00[Asia/Seoul]")
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문 생성 통합테스트")
    @Nested
    inner class PlaceOrder {

        @DisplayName("주문을 생성하면 재고가 감소하고 포인트가 차감되며 주문과 결제가 생성된다")
        @Test
        fun `create order with stock decrease and point deduction`() {
            // given
            val userId = 1L
            val product1 = createProduct(stock = Stock.of(100))
            val product2 = createProduct(stock = Stock.of(50))
            createPointAccount(userId = userId, balance = Money.krw(100000))

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(30000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product1.id, quantity = 2),
                    OrderCriteria.PlaceOrderItem(productId = product2.id, quantity = 1),
                ),
                issuedCouponId = null,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val updatedProduct1 = productRepository.findById(product1.id)!!
            val updatedProduct2 = productRepository.findById(product2.id)!!
            val updatedPointAccount = pointAccountRepository.findByUserId(userId)!!

            assertAll(
                { assertThat(orderInfo.orderId).isGreaterThan(0) },
                { assertThat(updatedProduct1.stock.amount).isEqualTo(98) },
                { assertThat(updatedProduct2.stock.amount).isEqualTo(49) },
                { assertThat(updatedPointAccount.balance).isEqualTo(Money.krw(70000)) },
            )
        }

        @DisplayName("주문 생성 시 주문 금액이 올바르게 계산된다")
        @Test
        fun `calculate total amount correctly when place order`() {
            // given
            val userId = 1L
            val product1 = createProduct(price = Money.krw(10000))
            val product2 = createProduct(price = Money.krw(20000))
            createPointAccount(userId = userId, balance = Money.krw(100000))

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(40000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product1.id, quantity = 2),
                    OrderCriteria.PlaceOrderItem(productId = product2.id, quantity = 1),
                ),
                issuedCouponId = null,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val savedOrder = orderRepository.findById(orderInfo.orderId)!!
            assertThat(savedOrder.totalAmount).isEqualTo(Money.krw(40000))
        }

        @DisplayName("재고가 부족하면 주문이 실패하고 모든 변경사항이 롤백된다")
        @Test
        fun `rollback all changes when stock is insufficient`() {
            // given
            val userId = 1L
            val normalStock = createProduct(stock = Stock.of(100))
            val insufficientStock = createProduct(stock = Stock.of(5))
            val pointAccount = createPointAccount(userId = userId, balance = Money.krw(100000))
            val initialBalance = pointAccount.balance

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(30000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = normalStock.id, quantity = 2),
                    OrderCriteria.PlaceOrderItem(productId = insufficientStock.id, quantity = 10),
                ),
                issuedCouponId = null,
            )

            // when
            val exception = assertThrows<CoreException> {
                orderFacade.placeOrder(criteria)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            // 모든 변경사항이 롤백되어야 함
            val unchangedNormalStock = productRepository.findById(normalStock.id)!!
            val unchangedInsufficientStock = productRepository.findById(insufficientStock.id)!!
            val unchangedPointAccount = pointAccountRepository.findByUserId(userId)!!

            assertAll(
                { assertThat(unchangedNormalStock.stock.amount).isEqualTo(100) },
                { assertThat(unchangedInsufficientStock.stock.amount).isEqualTo(5) },
                { assertThat(unchangedPointAccount.balance).isEqualTo(initialBalance) },
            )
        }

        @DisplayName("포인트가 부족하면 주문이 실패하고 재고 감소가 롤백된다")
        @Test
        fun `rollback stock decrease when point is insufficient`() {
            // given
            val userId = 1L
            val product = createProduct(stock = Stock.of(100))
            createPointAccount(userId = userId, balance = Money.krw(5000)) // 포인트 부족

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = null,
            )

            // when
            val exception = assertThrows<CoreException> {
                orderFacade.placeOrder(criteria)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            // 재고가 롤백되어야 함
            val unchangedProduct = productRepository.findById(product.id)!!
            assertThat(unchangedProduct.stock.amount).isEqualTo(100)
        }

        @DisplayName("여러 상품을 주문하면 모든 상품의 재고가 감소한다")
        @Test
        fun `decrease all product stocks when order multiple products`() {
            // given
            val userId = 1L
            val product1 = createProduct(stock = Stock.of(100))
            val product2 = createProduct(stock = Stock.of(50))
            val product3 = createProduct(stock = Stock.of(30))
            createPointAccount(userId = userId, balance = Money.krw(200000))

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(100000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product1.id, quantity = 5),
                    OrderCriteria.PlaceOrderItem(productId = product2.id, quantity = 3),
                    OrderCriteria.PlaceOrderItem(productId = product3.id, quantity = 2),
                ),
                issuedCouponId = null,
            )

            // when
            orderFacade.placeOrder(criteria)

            // then
            val updatedProduct1 = productRepository.findById(product1.id)!!
            val updatedProduct2 = productRepository.findById(product2.id)!!
            val updatedProduct3 = productRepository.findById(product3.id)!!

            assertAll(
                { assertThat(updatedProduct1.stock.amount).isEqualTo(95) },
                { assertThat(updatedProduct2.stock.amount).isEqualTo(47) },
                { assertThat(updatedProduct3.stock.amount).isEqualTo(28) },
            )
        }

        @DisplayName("존재하지 않는 상품으로 주문하면 예외가 발생한다")
        @Test
        fun `throw exception when order with non existing product`() {
            // given
            val userId = 1L
            val notExistProductId = 999L
            createPointAccount(userId = userId, balance = Money.krw(100000))

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = notExistProductId, quantity = 1),
                ),
                issuedCouponId = null,
            )

            // when
            val exception = assertThrows<CoreException> {
                orderFacade.placeOrder(criteria)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("존재하지 않는 상품입니다")
        }

        @DisplayName("정액 쿠폰을 적용하여 주문하면 쿠폰 할인이 적용된다")
        @Test
        fun `apply fixed amount coupon discount when place order`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000))
            createPointAccount(userId = userId, balance = Money.krw(100000))

            val coupon = createCoupon(
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = 5000,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(15000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findByOrderId(orderInfo.orderId)!!
            val updatedIssuedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!

            assertAll(
                { assertThat(payment.couponDiscount).isEqualTo(Money.krw(5000)) },
                { assertThat(payment.issuedCouponId).isEqualTo(issuedCoupon.id) },
                { assertThat(payment.totalAmount).isEqualTo(Money.krw(20000)) },
                { assertThat(payment.usedPoint).isEqualTo(Money.krw(15000)) },
                { assertThat(updatedIssuedCoupon.status).isEqualTo(UsageStatus.USED) },
            )
        }

        @DisplayName("정률 쿠폰을 적용하여 주문하면 쿠폰 할인이 적용된다")
        @Test
        fun `apply rate coupon discount when place order`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(50000))
            createPointAccount(userId = userId, balance = Money.krw(100000))

            val coupon = createCoupon(
                discountType = DiscountType.RATE,
                discountValue = 10,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(45000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findByOrderId(orderInfo.orderId)!!
            val updatedIssuedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!

            assertAll(
                { assertThat(payment.couponDiscount).isEqualTo(Money.krw(5000)) },
                { assertThat(payment.issuedCouponId).isEqualTo(issuedCoupon.id) },
                { assertThat(payment.totalAmount).isEqualTo(Money.krw(50000)) },
                { assertThat(payment.usedPoint).isEqualTo(Money.krw(45000)) },
                { assertThat(updatedIssuedCoupon.status).isEqualTo(UsageStatus.USED) },
            )
        }

        @DisplayName("할인 금액이 주문 금액보다 크면 주문 금액으로 제한된다")
        @Test
        fun `coupon discount is capped at order amount`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(10000))
            createPointAccount(userId = userId, balance = Money.krw(10000))

            val coupon = createCoupon(
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = 20000,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.ZERO_KRW,
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findByOrderId(orderInfo.orderId)!!

            assertAll(
                { assertThat(payment.couponDiscount).isEqualTo(Money.krw(10000)) },
                { assertThat(payment.totalAmount).isEqualTo(Money.krw(10000)) },
                { assertThat(payment.usedPoint).isEqualTo(Money.ZERO_KRW) },
            )
        }

        @DisplayName("이미 사용된 쿠폰으로 주문하면 예외가 발생하고 트랜잭션이 롤백된다")
        @Test
        fun `throw exception and rollback when using already used coupon`() {
            // given
            val userId = 1L
            val product = createProduct(stock = Stock.of(100))
            val pointAccount = createPointAccount(userId = userId, balance = Money.krw(100000))

            val coupon = createCoupon()
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            // 쿠폰을 먼저 사용 처리
            val couponEntity = couponRepository.findById(coupon.id)!!
            issuedCoupon.use(userId, couponEntity, Money.krw(10000), FIXED_TIME)
            issuedCouponRepository.save(issuedCoupon)

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(10000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val exception = assertThrows<CoreException> {
                orderFacade.placeOrder(criteria)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            // 재고와 포인트가 롤백되어야 함
            val unchangedProduct = productRepository.findById(product.id)!!
            val unchangedPointAccount = pointAccountRepository.findByUserId(userId)!!

            assertAll(
                { assertThat(unchangedProduct.stock.amount).isEqualTo(100) },
                { assertThat(unchangedPointAccount.balance).isEqualTo(pointAccount.balance) },
            )
        }

        @DisplayName("쿠폰 사용 후 포인트 부족 시 쿠폰과 재고가 롤백된다")
        @Test
        fun `rollback coupon and stock when point is insufficient after coupon discount`() {
            // given
            val userId = 1L
            val product = createProduct(price = Money.krw(20000), stock = Stock.of(100))
            createPointAccount(userId = userId, balance = Money.krw(10000)) // 부족한 포인트

            val coupon = createCoupon(
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = 5000,
            )
            val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)

            val criteria = OrderCriteria.PlaceOrder(
                userId = userId,
                usePoint = Money.krw(20000),
                items = listOf(
                    OrderCriteria.PlaceOrderItem(productId = product.id, quantity = 1),
                ),
                issuedCouponId = issuedCoupon.id,
            )

            // when
            val exception = assertThrows<CoreException> {
                orderFacade.placeOrder(criteria)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)

            // 쿠폰 상태, 재고, 포인트 모두 롤백되어야 함
            val unchangedIssuedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
            val unchangedProduct = productRepository.findById(product.id)!!

            assertAll(
                { assertThat(unchangedIssuedCoupon.status).isEqualTo(UsageStatus.AVAILABLE) },
                { assertThat(unchangedProduct.stock.amount).isEqualTo(100) },
            )
        }

        @DisplayName("쿠폰 없이 주문하면 쿠폰 할인이 적용되지 않는다")
        @Test
        fun `no coupon discount when order without coupon`() {
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
            )

            // when
            val orderInfo = orderFacade.placeOrder(criteria)

            // then
            val payment = paymentRepository.findByOrderId(orderInfo.orderId)!!

            assertAll(
                { assertThat(payment.couponDiscount).isEqualTo(Money.ZERO_KRW) },
                { assertThat(payment.issuedCouponId).isNull() },
                { assertThat(payment.totalAmount).isEqualTo(Money.krw(10000)) },
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
        val issuedCoupon = IssuedCoupon.issue(userId = userId, coupon = coupon)
        return issuedCouponRepository.save(issuedCoupon)
    }
}
