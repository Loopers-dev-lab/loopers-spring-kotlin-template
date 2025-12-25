package com.loopers.domain.product

import com.loopers.fixtures.TestFixtures
import com.loopers.fixtures.createTestBrand
import com.loopers.fixtures.createTestProduct
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Stock 동시성 테스트")
class StockConcurrencyTest @Autowired constructor(
    private val stockService: StockService,
    private val testFixtures: TestFixtures,
) {
    private var productId by Delegates.notNull<Long>()

    @BeforeEach
    fun setUp() {
        testFixtures.clear()

        val brand = testFixtures.saveBrand(createTestBrand())
        val product = testFixtures.saveProduct(createTestProduct(brand = brand))
        productId = product.id

        // 초기 재고 100개 설정
        testFixtures.saveStock(
            Stock(
                productId = productId,
                quantity = 100,
            ),
        )
    }

    @Test
    @DisplayName("동일한 상품에 대해 여러 주문이 동시에 요청되어도 재고가 정상적으로 차감되어야 한다")
    fun concurrency_multipleOrdersOnSameProduct_shouldDeductStockCorrectly() {
        // given
        val numberOfOrders = 10
        val quantityPerOrder = 5
        val latch = CountDownLatch(numberOfOrders)
        val executor = Executors.newFixedThreadPool(numberOfOrders)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when: 10개의 주문이 동시에 5개씩 재고 차감 시도 (총 50개)
        repeat(numberOfOrders) {
            executor.submit {
                try {
                    stockService.decreaseStock(productId, quantityPerOrder)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("재고 차감 실패: ${e.message}")
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 모두 성공해야 함 (100개에서 50개 차감)
        assertThat(successCount.get()).isEqualTo(10)
        assertThat(failureCount.get()).isEqualTo(0)

        // 최종 재고 확인
        val stock = stockService.getStockByProductId(productId)
        assertThat(stock.quantity).isEqualTo(50)
    }

    @Test
    @DisplayName("재고가 부족한 상황에서 동시에 여러 주문이 들어오면 일부만 성공해야 한다")
    fun concurrency_insufficientStock_shouldPartiallySucceed() {
        // given
        val numberOfOrders = 10
        val quantityPerOrder = 15
        val latch = CountDownLatch(numberOfOrders)
        val executor = Executors.newFixedThreadPool(numberOfOrders)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when: 10개의 주문이 동시에 15개씩 재고 차감 시도 (총 150개 필요하지만 100개만 있음)
        repeat(numberOfOrders) {
            executor.submit {
                try {
                    stockService.decreaseStock(productId, quantityPerOrder)
                    successCount.incrementAndGet()
                } catch (e: com.loopers.support.error.CoreException) {
                    // 재고 부족 예외만 실패로 카운트
                    if (e.customMessage?.contains("재고 부족") == true) {
                        failureCount.incrementAndGet()
                    } else {
                        // 예상하지 못한 CoreException은 재발생
                        throw e
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 100 / 15 = 6번 성공, 4번 실패
        assertThat(successCount.get()).isEqualTo(6)
        assertThat(failureCount.get()).isEqualTo(4)

        // 최종 재고 확인 (15 * 6 = 90 차감)
        val stock = stockService.getStockByProductId(productId)
        assertThat(stock.quantity).isEqualTo(10)
    }

    @Test
    @DisplayName("재고 증가와 감소가 동시에 일어나도 정합성이 보장되어야 한다")
    fun concurrency_increaseAndDecreaseSimultaneously_shouldMaintainConsistency() {
        // given
        val numberOfOperations = 20
        val amount = 5
        val latch = CountDownLatch(numberOfOperations)
        val executor = Executors.newFixedThreadPool(numberOfOperations)
        val increaseCount = AtomicInteger(0)
        val decreaseCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when: 10번 증가, 10번 감소가 동시에 발생
        repeat(numberOfOperations) { index ->
            executor.submit {
                try {
                    if (index % 2 == 0) {
                        stockService.increaseStock(productId, amount)
                        increaseCount.incrementAndGet()
                    } else {
                        stockService.decreaseStock(productId, amount)
                        decreaseCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    println("재고 작업 실패 (index=$index): ${e.message}")
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 모든 작업이 성공했는지 확인
        assertThat(failureCount.get()).isEqualTo(0)
        assertThat(increaseCount.get() + decreaseCount.get()).isEqualTo(numberOfOperations)

        // 최종 재고 = 초기 100 + (증가 횟수 - 감소 횟수) * 5
        val stock = stockService.getStockByProductId(productId)
        val expectedQuantity = 100 + (increaseCount.get() - decreaseCount.get()) * amount
        assertThat(stock.quantity).isEqualTo(expectedQuantity)
    }
}
