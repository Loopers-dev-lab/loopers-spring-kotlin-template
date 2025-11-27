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
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class OrderFacadeConcurrencyTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val pointAccountRepository: PointAccountRepository,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문해도, 쿠폰은 단 한번만 사용되어야 한다")
    @Test
    fun `same coupon can only be used once even with concurrent orders`() {
        // given
        val userId = 1L
        val product = createProduct(price = Money.krw(10000))
        val coupon = createCoupon(discountType = DiscountType.FIXED_AMOUNT, discountValue = 5000)
        val issuedCoupon = createIssuedCoupon(userId = userId, coupon = coupon)
        createPointAccount(userId = userId)

        val threadCount = 5
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when
        repeat(threadCount) {
            executorService.submit {
                try {
                    val criteria = OrderCriteria.PlaceOrder(
                        userId = userId,
                        items = listOf(
                            OrderCriteria.PlaceOrderItem(
                                productId = product.id,
                                quantity = 1,
                            ),
                        ),
                        usePoint = Money.krw(5000),
                        issuedCouponId = issuedCoupon.id,
                    )
                    orderFacade.placeOrder(criteria)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failureCount.get()).isEqualTo(threadCount - 1)

        val updatedIssuedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
        assertThat(updatedIssuedCoupon.status).isEqualTo(UsageStatus.USED)
        assertThat(updatedIssuedCoupon.usedAt).isNotNull()
    }

    @DisplayName("동일한 유저가 서로 다른 주문을 동시에 수행해도, 포인트가 정상적으로 차감되어야 한다")
    @Test
    fun `concurrent orders from same user should deduct points correctly`() {
        // given
        val userId = 1L
        val product1 = createProduct()
        val product2 = createProduct()
        val product3 = createProduct()
        createPointAccount(userId = userId)

        val threadCount = 3
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        val products = listOf(product1, product2, product3)

        // when
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    val criteria = OrderCriteria.PlaceOrder(
                        userId = userId,
                        items = listOf(
                            OrderCriteria.PlaceOrderItem(
                                productId = products[index].id,
                                quantity = 1,
                            ),
                        ),
                        usePoint = products[index].price,
                        issuedCouponId = null,
                    )
                    orderFacade.placeOrder(criteria)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get()).isEqualTo(3)
    }

    @DisplayName("동일한 상품에 대해 여러 주문이 동시에 요청되어도, 재고가 정상적으로 차감되어야 한다")
    @Test
    fun `concurrent orders for same product should deduct stock correctly`() {
        // given
        val initialStock = 10
        val product = createProduct(stock = Stock.of(initialStock))

        val threadCount = 10
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        repeat(threadCount) { index ->
            val userId = index + 1L
            createPointAccount(userId = userId)
        }

        // when
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    val userId = index + 1L
                    val criteria = OrderCriteria.PlaceOrder(
                        userId = userId,
                        items = listOf(
                            OrderCriteria.PlaceOrderItem(
                                productId = product.id,
                                quantity = 1,
                            ),
                        ),
                        usePoint = product.price,
                        issuedCouponId = null,
                    )
                    orderFacade.placeOrder(criteria)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        assertThat(successCount.get()).isEqualTo(initialStock)

        val updatedProduct = productRepository.findById(product.id)!!
        assertThat(updatedProduct.stock.amount).isEqualTo(0)
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
        userId: Long,
        balance: Money = Money.krw(1_000_000),
    ): PointAccount {
        val account = PointAccount.of(userId, balance)
        return pointAccountRepository.save(account)
    }

    private fun createCoupon(
        name: String = "테스트 쿠폰",
        discountType: DiscountType,
        discountValue: Long,
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
