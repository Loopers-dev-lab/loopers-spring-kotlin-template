package com.loopers.domain.product

import com.loopers.IntegrationTest
import com.loopers.domain.brand.Brand
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderDetail
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.order.OrderDetailJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.StockJpaRepository
import junit.framework.TestCase.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StockConcurrencyTest : IntegrationTest() {

    private val log = LoggerFactory.getLogger(StockConcurrencyTest::class.java)

    @Autowired
    private lateinit var productService: ProductService

    @Autowired
    private lateinit var stockJpaRepository: StockJpaRepository

    @Autowired
    private lateinit var productJpaRepository: ProductJpaRepository

    @Autowired
    private lateinit var brandJpaRepository: BrandJpaRepository

    @Autowired
    private lateinit var orderJpaRepository: OrderJpaRepository

    @Autowired
    private lateinit var orderDetailJpaRepository: OrderDetailJpaRepository

    @Test
    fun `30개의 동시 재고 차감 요청시 Lost Update 없이 정확히 0이 된다`() {
        // given
        val userId = 1L
        val brand = createAndSaveBrand("테스트브랜드")
        val product = createAndSaveProduct("상품1", 1L, brand.id)
        createAndSaveStock(30L, product.id)

        // when
        val threadCount = 30
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        repeat(threadCount) {
            executor.submit {
                val threadName = Thread.currentThread().name
                try {
                    log.info("[$threadName] 재고 차감 시도")

                    // 주문 및 주문 상세 생성
                    val order = orderJpaRepository.save(Order.create(1L, userId))
                    val orderDetail = orderDetailJpaRepository.save(
                        OrderDetail.create(1L, brand, product, order),
                    )

                    productService.deductAllStock(listOf(orderDetail))
                    successCount.incrementAndGet()
                    log.info("[$threadName] 재고 차감 성공")
                } catch (e: Exception) {
                    // 예외는 정상적인 동작(재고 부족, 락 획득 실패 등)
                    failCount.incrementAndGet()
                    log.warn("[$threadName] 재고 차감 실패: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        val finalStock = stockJpaRepository.findByIdOrNull(product.id)!!.quantity

        assertSoftly { soft ->
            // Lost Update 발생 시 finalStock > 0
            soft.assertThat(finalStock).isEqualTo(0L)
            soft.assertThat(successCount.get()).isEqualTo(threadCount)
            soft.assertThat(failCount.get()).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("데드락 방지 검증")
    inner class DeadlockPrevention {

        @Test
        fun `동일한 상품들을 다른 순서로 동시 주문해도 데드락이 발생하지 않는다`() {
            val userId = 1L
            val brand = createAndSaveBrand("테스트브랜드")

            val product1 = createAndSaveProduct("상품1", 1000L, brand.id)
            val product2 = createAndSaveProduct("상품2", 2000L, brand.id)

            createAndSaveStock(100L, product1.id)
            createAndSaveStock(100L, product2.id)

            val executor = Executors.newFixedThreadPool(2)
            val readyLatch = CountDownLatch(2) // 준비 완료 신호
            val startLatch = CountDownLatch(1) // 동시 시작 신호
            val endLatch = CountDownLatch(2) // 완료 신호

            val exceptions = mutableListOf<Exception>()

            // when - 두 스레드가 정확히 동시에 시작
            executor.submit {
                val threadName = Thread.currentThread().name
                readyLatch.countDown() // 준비 완료
                startLatch.await() // 시작 신호 대기
                try {
                    log.info("[$threadName] 주문1 시작 (상품1→상품2)")

                    // 주문1: 상품1 → 상품2 순서
                    val order1 = orderJpaRepository.save(Order.create(30000L, userId))
                    val order1Details = listOf(
                        orderDetailJpaRepository.save(OrderDetail.create(10L, brand, product1, order1)),
                        orderDetailJpaRepository.save(OrderDetail.create(10L, brand, product2, order1)),
                    )

                    productService.deductAllStock(order1Details)
                    log.info("[$threadName] 주문1 성공")
                } catch (e: Exception) {
                    log.warn("[$threadName] 주문1 실패: ${e.message}")
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                } finally {
                    endLatch.countDown()
                }
            }

            executor.submit {
                val threadName = Thread.currentThread().name
                readyLatch.countDown() // 준비 완료
                startLatch.await() // 시작 신호 대기
                try {
                    log.info("[$threadName] 주문2 시작 (상품2→상품1)")

                    // 주문2: 상품2 → 상품1 순서 (역순)
                    val order2 = orderJpaRepository.save(Order.create(30000L, userId))
                    val order2Details = listOf(
                        orderDetailJpaRepository.save(OrderDetail.create(10L, brand, product2, order2)),
                        orderDetailJpaRepository.save(OrderDetail.create(10L, brand, product1, order2)),
                    )

                    productService.deductAllStock(order2Details)
                    log.info("[$threadName] 주문2 성공")
                } catch (e: Exception) {
                    log.warn("[$threadName] 주문2 실패: ${e.message}")
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                } finally {
                    endLatch.countDown()
                }
            }

            // 두 스레드 준비 완료 대기
            readyLatch.await()

            // 동시에 시작
            log.info("모든 스레드 준비 완료, 동시 시작!")
            startLatch.countDown()

            // Then: 5초 내에 완료되어야 함 (데드락 없음)
            assertDoesNotThrow("데드락이 발생하면 타임아웃") {
                assertTrue(
                    "5초 내에 완료되지 않음 - 데드락 의심",
                    endLatch.await(5, TimeUnit.SECONDS),
                )
            }

            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)

            // 데드락 관련 예외 확인
            val deadlockExceptions = exceptions.filter { e ->
                val message = e.message?.lowercase() ?: ""
                val causeMessage = e.cause?.message?.lowercase() ?: ""
                message.contains("deadlock") || causeMessage.contains("deadlock")
            }

            assertThat(deadlockExceptions)
                .`as`("데드락 예외가 발생하지 않아야 함")
                .isEmpty()

            log.info("✅ 데드락 없이 정상 완료")
            log.info("총 예외: ${exceptions.size}개")
        }
    }

    private fun createAndSaveBrand(name: String): Brand {
        return brandJpaRepository.save(Brand.create(name))
    }

    private fun createAndSaveProduct(name: String, price: Long, brandId: Long): Product {
        return productJpaRepository.save(Product.create(name, price, brandId))
    }

    private fun createAndSaveStock(quantity: Long, productId: Long): Stock {
        return stockJpaRepository.save(Stock.create(quantity, productId))
    }
}
