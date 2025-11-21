package com.loopers.domain.stock

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaRepository
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
class StockIntegrationTest @Autowired constructor(
    private val stockService: StockService,
    private val stockJpaRepository: StockJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    private lateinit var product: Product
    private lateinit var stock: Stock

    @BeforeEach
    fun setUp() {
        val brand = brandJpaRepository.save(
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

    @DisplayName("동일한 상품에 대해 여러 주문이 동시에 요청되어도, 재고가 정상적으로 차감되어야 한다")
    @Test
    fun whenMultipleOrdersDeductStockConcurrently_thenStockShouldBeDeductedCorrectly() {
        // arrange
        val threadCount = 10
        val deductQuantity = 5
        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // act
        repeat(threadCount) {
            executorService.submit {
                try {
                    stockService.deductStock(product.id, deductQuantity)
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
        val updatedStock = stockJpaRepository.findById(stock.id).orElseThrow()
        val expectedQuantity = 100 - (deductQuantity * successCount.get())

        assertThat(updatedStock.quantity).isEqualTo(expectedQuantity)
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount)
    }
}
