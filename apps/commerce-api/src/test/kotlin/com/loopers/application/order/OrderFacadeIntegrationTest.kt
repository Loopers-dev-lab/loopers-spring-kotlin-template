package com.loopers.application.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.point.Point
import com.loopers.domain.product.Product
import com.loopers.domain.stock.Stock
import com.loopers.domain.user.User
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
class OrderFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userJpaRepository: UserJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val pointJpaRepository: PointJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    private lateinit var brand: Brand
    private lateinit var product: Product
    private lateinit var stock: Stock

    @BeforeEach
    fun setUp() {
        brand = brandJpaRepository.save(
            Brand.of(name = "Test Brand"),
        )

        product = productJpaRepository.save(
            Product.of(
                name = "Test Product",
                price = BigDecimal("10000.00"),
                brandId = brand.id,
            ),
        )

        stock = stockJpaRepository.save(
            Stock.of(
                productId = product.id,
                quantity = 100,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 유저가 서로 다른 주문을 동시에 수행해도, 포인트가 충분하면 모두 성공해야 한다")
    @Test
    fun whenSameUserOrdersConcurrently_withSufficientPoint_thenAllShouldSucceed() {
        // arrange
        val user = userJpaRepository.save(
            User(
                username = "testuser",
                password = "password123",
                email = "test@example.com",
                birthDate = "1997-03-25",
                gender = User.Gender.MALE,
            ),
        )

        val point = pointJpaRepository.save(
            Point.of(
                userId = user.id,
                initialBalance = BigDecimal("1000000.00"),
            ),
        )

        // 재고를 충분히 설정
        stock.increase(900)
        stockJpaRepository.save(stock)

        val threadCount = 3
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // act
        repeat(threadCount) {
            executorService.submit {
                try {
                    orderFacade.createOrder(
                        userId = user.id,
                        items = listOf(OrderItemCommand(productId = product.id, quantity = 1)),
                    )
                    successCount.incrementAndGet()
                } catch (e: CoreException) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // assert
        assertThat(successCount.get()).isEqualTo(threadCount)
        assertThat(failCount.get()).isEqualTo(0)

        val updatedPoint = pointJpaRepository.findById(point.id).orElseThrow()
        val expectedBalance = BigDecimal("1000000.00").subtract(
            BigDecimal("10000.00").multiply(BigDecimal(threadCount)),
        )
        assertThat(updatedPoint.balance).isEqualByComparingTo(expectedBalance)

        val orders = orderJpaRepository.findAll()
        assertThat(orders).hasSize(threadCount)
    }

    @DisplayName("동일한 상품에 대해 여러 유저가 동시에 주문해도, 재고가 충분하면 모두 성공해야 한다")
    @Test
    fun whenMultipleUsersOrderSameProductConcurrently_withSufficientStock_thenAllShouldSucceed() {
        // arrange
        val threadCount = 10
        val userIds = mutableListOf<Long>()

        repeat(threadCount) { i ->
            val user = userJpaRepository.save(
                User(
                    username = "user$i",
                    password = "password123",
                    email = "user$i@test.com",
                    birthDate = "1997-03-25",
                    gender = User.Gender.MALE,
                ),
            )
            pointJpaRepository.save(
                Point.of(
                    userId = user.id,
                    initialBalance = BigDecimal("100000.00"),
                ),
            )
            userIds.add(user.id)
        }

        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // act
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    orderFacade.createOrder(
                        userId = userIds[index],
                        items = listOf(OrderItemCommand(productId = product.id, quantity = 1)),
                    )
                    successCount.incrementAndGet()
                } catch (e: CoreException) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // assert
        assertThat(successCount.get()).isEqualTo(threadCount)
        assertThat(failCount.get()).isEqualTo(0)

        val updatedStock = stockJpaRepository.findById(stock.id).orElseThrow()
        val expectedQuantity = 100 - threadCount
        assertThat(updatedStock.quantity).isEqualTo(expectedQuantity)

        val orders = orderJpaRepository.findAll()
        assertThat(orders).hasSize(threadCount)
    }
}
