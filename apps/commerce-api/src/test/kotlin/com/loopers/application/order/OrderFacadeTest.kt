package com.loopers.application.order

import com.loopers.IntegrationTestSupport
import com.loopers.domain.common.vo.Money
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.point.PointModel
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.stock.StockModel
import com.loopers.domain.user.UserFixture
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.concurrent.CompletableFuture
import kotlin.test.Test

class OrderFacadeTest(
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserJpaRepository,
    private val pointRepository: PointJpaRepository,
    private val productRepository: ProductJpaRepository,
    private val orderRepository: OrderJpaRepository,
    private val stockRepository: StockJpaRepository,
    private val couponRepository: CouponJpaRepository,
    private val orderFacade: OrderFacade,
) : IntegrationTestSupport() {

    @AfterEach
    fun teardown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문하기")
    @Nested
    inner class Order {

        @DisplayName("주문이 정상적으로 이루어진다.")
        @Test
        fun orderSuccess() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val testPoint = PointModel(
                user.id,
                Money(BigDecimal.valueOf(10000L)),
            )
            pointRepository.save(testPoint)

            val testProduct = ProductModel.create(
                "상품 123",
                Money(BigDecimal.valueOf(10000L)),
                1L,
            )
            productRepository.save(testProduct)

            val testStock = StockModel.create(testProduct.id, 1000L)
            stockRepository.save(testStock)

            val command = OrderCommand(
                orderItems = listOf(OrderItemCommand(testProduct.id, 10L, BigDecimal.valueOf(500L))),
                cardType = "CREDIT",
                cardNo = "1234-5678-9012-3456",
                couponId = null
            )

            // act & assert
            orderFacade.order(user.id, command)

            val orders = orderRepository.findAll()
            val point = pointRepository.findById(testPoint.id).get()
            val stock = stockRepository.findById(testStock.id).get()

            assertAll(
                { assertThat(orders.size).isEqualTo(1) },
                { assertThat(point.balance.amount).isEqualByComparingTo(BigDecimal.valueOf(5000L)) },
                { assertThat(stock.amount).isEqualTo(990L) },
            )
        }

        @DisplayName("쿠폰이 존재하지 않는 경우 , 주문은 실패한다.")
        @Test
        fun orderFails_whenCouponDoesNotExist() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val testPoint = PointModel(
                user.id,
                Money(BigDecimal.valueOf(10000L)),
            )
            pointRepository.save(testPoint)

            val testProduct = ProductModel.create(
                "상품 123",
                Money(BigDecimal.valueOf(10000L)),
                1L,
            )
            productRepository.save(testProduct)

            val testStock = StockModel.create(testProduct.id, 100L)
            stockRepository.save(testStock)

            val nonExistentCouponId = 999L
            val command = OrderCommand(
                orderItems = listOf(OrderItemCommand(testProduct.id, 1L, BigDecimal.valueOf(10000L))),
                cardType = "CREDIT",
                cardNo = "1234-5678-9012-3456",
                couponId = nonExistentCouponId
            )

            // act & assert
            val exception = assertThrows<CoreException> {
                orderFacade.order(user.id, command)
            }

            val orders = orderRepository.findAll()

            assertAll(
                { assertThat(exception.message).contains("쿠폰") },
                { assertThat(orders).isEmpty() },
            )
        }

        @DisplayName("유저 포인트 잔액이 부족할 경우, 주문은 실패한다.(모두 롤백 처리)")
        @Test
        fun orderFails_whenInsufficientBalance() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val testPoint = PointModel(
                user.id,
                Money(BigDecimal.ZERO),
            )
            pointRepository.save(testPoint)

            val testProduct = ProductModel.create(
                "상품 123",
                Money(BigDecimal.valueOf(10000L)),
                1L,
            )
            productRepository.save(testProduct)

            val testStock = StockModel.create(testProduct.id, 100L)
            stockRepository.save(testStock)

            // act & assert
            val exception = assertThrows<CoreException> {
                orderFacade.order(
                    user.id,
                    OrderCommand(
                        orderItems = listOf(OrderItemCommand(testProduct.id, 10L, BigDecimal.valueOf(500L))),
                        cardType = "CREDIT",
                        cardNo = "1234-5678-9012-3456",
                        couponId = null
                    )
                )
            }
            val orders = orderRepository.findAll()
            val point = pointRepository.findById(user.id).get()
            val stock = stockRepository.findByRefProductId(testProduct.id)!!

            assertAll(
                { assertThat(exception.message).isEqualTo("잔액이 부족합니다.") },
                { assertThat(orders).isEmpty() },
                { assertThat(point.balance.amount).isEqualByComparingTo(BigDecimal.ZERO) },
                { assertThat(stock.amount).isEqualTo(100L) },
            )
        }

        @DisplayName("재고가 부족한 경우에, 주문을 실패한다. (모두 롤백처리)")
        @Test
        fun orderFails_whenInsufficientStock() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val testPoint = PointModel(
                user.id,
                Money(BigDecimal.valueOf(5000L)),
            )
            pointRepository.save(testPoint)

            val testProduct = ProductModel.create(
                "상품 123",
                Money(BigDecimal.valueOf(10000L)),
                1L,
            )
            productRepository.save(testProduct)

            val testStock = StockModel.create(testProduct.id, 0L)
            stockRepository.save(testStock)

            // act & assert
            val exception = assertThrows<CoreException> {
                orderFacade.order(
                    user.id,
                    OrderCommand(
                        orderItems = listOf(OrderItemCommand(testProduct.id, 10L, BigDecimal.valueOf(500L))),
                        cardType = "CREDIT",
                        cardNo = "1234-5678-9012-3456",
                        couponId = null
                    )
                )
            }
            val orders = orderRepository.findAll()
            val point = pointRepository.findById(user.id).get()
            val stock = stockRepository.findByRefProductId(testProduct.id)!!

            assertAll(
                { assertThat(exception.message).isEqualTo("재고가 부족합니다.") },
                { assertThat(orders).isEmpty() },
                { assertThat(point.balance.amount).isEqualByComparingTo(BigDecimal.valueOf(5000L)) },
                { assertThat(stock.amount).isEqualTo(0L) },
            )
        }

        @DisplayName("동일한 유저가 서로 다른 주문 10개를 동시에 수행해도, 포인트가 정상적으로 차감된다.")
        @Test
        fun pointPaySuccess_whenSameUserOrdersOtherOrders() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val testPoint = PointModel(
                user.id,
                Money(BigDecimal.valueOf(50000L)),
            )
            pointRepository.save(testPoint)

            val products = (1..10).map { i ->
                val product = ProductModel.create(
                    "상품 $i",
                    Money(BigDecimal.valueOf(5000L)),
                    i.toLong(),
                )
                productRepository.save(product)

                val stock = StockModel.create(product.id, 100L)
                stockRepository.save(stock)

                product
            }

            // act
            val futures = products.map { product ->
                CompletableFuture.supplyAsync {
                    val command = OrderCommand(
                        orderItems = listOf(OrderItemCommand(product.id, 1L, BigDecimal.valueOf(5000L))),
                        cardType = "CREDIT",
                        cardNo = "1234-5678-9012-3456",
                        couponId = null
                    )
                    orderFacade.order(user.id, command)
                }
            }

            val results = futures.map { it.join() }
            val successCount = results.count()
            val point = pointRepository.findById(user.id).get()

            // assert
            assertAll(
                { assertThat(successCount).isEqualTo(10) },
                { assertThat(point.balance.amount).isEqualByComparingTo(BigDecimal.ZERO) },
            )
        }

        @DisplayName("동일한 상품에 대해 주문이 10개가 동시에 요청되어도, 재고가 정상적으로 차감되어야 한다.")
        @Test
        fun stockOccupySuccess_whenMultipleOrdersInSameProduct() {
            // arrange
            val users = (1..10).map { i ->
                val user = UserFixture.create(
                    loginId = "test$i",
                )
                userRepository.save(user)

                val point = PointModel(
                    user.id,
                    Money(BigDecimal.valueOf(10000L)),
                )
                pointRepository.save(point)
                user
            }

            val testProduct = ProductModel.create(
                "인기 상품",
                Money(BigDecimal.valueOf(1000L)),
                1L,
            )
            productRepository.save(testProduct)

            val testStock = StockModel.create(testProduct.id, 100L)
            stockRepository.save(testStock)

            // act
            val futures = users.map { user ->
                CompletableFuture.supplyAsync {
                    val command = OrderCommand(
                        orderItems = listOf(OrderItemCommand(testProduct.id, 10L, BigDecimal.valueOf(1000L))),
                        cardType = "CREDIT",
                        cardNo = "1234-5678-9012-3456",
                        couponId = null
                    )
                    orderFacade.order(user.id, command)
                }
            }

            val results = futures.map { it.join() }
            val successCount = results.count()
            val stock = stockRepository.findByRefProductId(testProduct.id)!!

            // assert
            assertAll(
                { assertThat(successCount).isEqualTo(10) },
                { assertThat(stock.amount).isEqualTo(0L) },
            )
        }

        @DisplayName("동일한 쿠폰으로 여러 기기에서 동시에 주문해도, 쿠폰은 단 한번만 사용되어야 한다.")
        @Test
        fun onlyOneOrderSuccess_whenMultipleOrdersWithSameCoupon() {
            // arrange
            val users = (1..2).map { i ->
                val user = UserFixture.create(
                    loginId = "coupon$i",
                )
                userRepository.save(user)

                val point = PointModel(
                    user.id,
                    Money(BigDecimal.valueOf(10000L)),
                )
                pointRepository.save(point)
            }

            val testProduct = ProductModel.create(
                "테스트 상품",
                Money(BigDecimal.valueOf(5000L)),
                1L,
            )
            productRepository.save(testProduct)

            val testStock = StockModel.create(testProduct.id, 100L)
            stockRepository.save(testStock)

            // 1000원 정액 할인 쿠폰 생성 (유저 1에게 발급)
            val testCoupon = CouponModel.create(
                refUserId = users[0].id,
                name = "1000원 할인 쿠폰",
                discountType = DiscountType.FIXED_AMOUNT,
                discountValue = BigDecimal.valueOf(1000),
            )
            couponRepository.save(testCoupon)

            // act - 두 유저가 동시에 같은 쿠폰으로 주문 시도
            val futures = users.map { user ->
                CompletableFuture.supplyAsync {
                    try {
                        val command = OrderCommand(
                            orderItems = listOf(OrderItemCommand(testProduct.id, 1L, BigDecimal.valueOf(5000L))),
                            cardType = "CREDIT",
                            cardNo = "1234-5678-9012-3456",
                            couponId = testCoupon.id
                        )
                        orderFacade.order(user.id, command)
                        true // 성공
                    } catch (e: Exception) {
                        false // 실패
                    }
                }
            }

            val results = futures.map { it.join() }
            val successCount = results.count { it }
            val orders = orderRepository.findAll()
            val updatedCoupon = couponRepository.findById(testCoupon.id).get()

            // assert
            assertAll(
                // 한 주문만 성공
                { assertThat(successCount).isEqualTo(1) },
                // 주문도 한 건만 생성
                { assertThat(orders.size).isEqualTo(1) },
                // 쿠폰이 사용됨
                { assertThat(updatedCoupon.isUsed).isTrue() },
            )
        }
    }
}
