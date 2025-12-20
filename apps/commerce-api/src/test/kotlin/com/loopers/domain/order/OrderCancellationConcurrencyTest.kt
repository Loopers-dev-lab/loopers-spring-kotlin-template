package com.loopers.domain.order

import com.loopers.application.order.OrderCreateRequest
import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderItemRequest
import com.loopers.domain.brand.Brand
import com.loopers.domain.point.Point
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
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
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("주문 취소 동시성 테스트")
class OrderCancellationConcurrencyTest {

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var orderFacade: OrderFacade

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var stockRepository: StockRepository

    @Autowired
    private lateinit var testFixtures: TestFixtures

    private lateinit var user: User
    private lateinit var brand: Brand
    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        testFixtures.clear()

        // 사용자 생성
        user = testFixtures.saveUser(
            name = "테스트유저",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 1),
        )

        // 포인트 생성
        testFixtures.savePoint(
            Point(
                userId = user.id,
                balance = Money(BigDecimal("100000000"), Currency.KRW),
            ),
        )

        // 브랜드 생성
        brand = testFixtures.saveBrand(createTestBrand(name = "동시성테스트브랜드"))

        // 상품 생성
        product = testFixtures.saveProduct(
            createTestProduct(
                name = "동시성테스트상품",
                price = BigDecimal("10000"),
                brand = brand,
            ),
        )

        // 재고 생성 (초기 재고 100)
        testFixtures.saveStock(Stock(productId = product.id, quantity = 100))
    }

    @Test
    @DisplayName("동일한 상품에 대해 여러 주문이 동시에 취소되어도 재고가 정상적으로 복구되어야 한다")
    fun concurrency_multipleCancellationsOnSameProduct_shouldRestoreStockCorrectly() {
        // given: 10개의 주문 생성 (각 주문당 상품 5개씩)
        val numberOfOrders = 10
        val quantityPerOrder = 5
        val orderIds = mutableListOf<Long>()

        repeat(numberOfOrders) {
            val request = OrderCreateRequest(
                items = listOf(
                    OrderItemRequest(productId = product.id, quantity = quantityPerOrder),
                ),
            )
            val orderInfo = orderFacade.createOrder(user.id, request)
            orderIds.add(orderInfo.orderId)
        }

        // 주문 생성 후 재고 확인 (100 - 50 = 50)
        val stockAfterOrders = requireNotNull(stockRepository.findByProductId(product.id))
        assertThat(stockAfterOrders.quantity).isEqualTo(50)

        val latch = CountDownLatch(numberOfOrders)
        val executor = Executors.newFixedThreadPool(numberOfOrders)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when: 10개의 주문을 동시에 취소 (각 5개씩 재고 복구, 총 50개)
        orderIds.forEach { orderId ->
            executor.submit {
                try {
                    orderService.cancelOrder(orderId, user.id)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("주문 취소 실패 (orderId=$orderId): ${e.message}")
                    failureCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 모두 성공해야 함
        assertThat(successCount.get()).isEqualTo(numberOfOrders)
        assertThat(failureCount.get()).isEqualTo(0)

        // 최종 재고 확인 (50 + 50 = 100)
        val finalStock = requireNotNull(stockRepository.findByProductId(product.id))
        assertThat(finalStock.quantity).isEqualTo(100)

        // 모든 주문이 CANCELLED 상태인지 확인
        orderIds.forEach { orderId ->
            val order = requireNotNull(orderRepository.findById(orderId))
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        }
    }

    @Test
    @DisplayName("동일한 주문을 여러 번 취소하려고 시도해도 한 번만 취소되고 재고는 정확히 복구되어야 한다")
    fun concurrency_multipleCancellationsOnSameOrder_shouldCancelOnlyOnceAndRestoreStockCorrectly() {
        // given: 1개의 주문 생성
        val quantity = 10
        val request = OrderCreateRequest(
            items = listOf(
                OrderItemRequest(productId = product.id, quantity = quantity),
            ),
        )
        val orderInfo = orderFacade.createOrder(user.id, request)
        val orderId = orderInfo.orderId

        // 주문 생성 후 재고 확인 (100 - 10 = 90)
        val stockAfterOrder = requireNotNull(stockRepository.findByProductId(product.id))
        assertThat(stockAfterOrder.quantity).isEqualTo(90)

        val numberOfAttempts = 10
        val latch = CountDownLatch(numberOfAttempts)
        val executor = Executors.newFixedThreadPool(numberOfAttempts)
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)

        // when: 동일한 주문을 동시에 여러 번 취소 시도
        repeat(numberOfAttempts) {
            executor.submit {
                try {
                    orderService.cancelOrder(orderId, user.id)
                    successCount.incrementAndGet()
                } catch (e: com.loopers.support.error.CoreException) {
                    // 이미 취소된 주문이라는 예외는 정상
                    if (e.customMessage?.contains("이미 취소된 주문입니다") == true) {
                        failureCount.incrementAndGet()
                    } else {
                        println("예상하지 못한 예외: ${e.message}")
                        throw e
                    }
                } catch (e: Exception) {
                    println("주문 취소 실패: ${e.message}")
                    throw e
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // then: 정확히 한 번만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1)
        assertThat(failureCount.get()).isEqualTo(numberOfAttempts - 1)

        // 재고는 정확히 한 번만 복구되어야 함 (90 + 10 = 100)
        val finalStock = requireNotNull(stockRepository.findByProductId(product.id))
        assertThat(finalStock.quantity).isEqualTo(100)

        // 주문 상태 확인
        val cancelledOrder = requireNotNull(orderRepository.findById(orderId))
        assertThat(cancelledOrder.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    @DisplayName("순차적으로 여러 주문을 취소하면 재고가 정확히 복구된다")
    fun sequential_multipleCancellations_shouldRestoreStockCorrectly() {
        // given: 10개의 주문 생성 (각 5개씩, 총 50개 차감)
        val orderIds = mutableListOf<Long>()
        repeat(10) {
            val request = OrderCreateRequest(
                items = listOf(
                    OrderItemRequest(productId = product.id, quantity = 5),
                ),
            )
            val orderInfo = orderFacade.createOrder(user.id, request)
            orderIds.add(orderInfo.orderId)
        }

        // 주문 생성 후 재고: 100 - 50 = 50
        val stockAfterOrders = requireNotNull(stockRepository.findByProductId(product.id))
        assertThat(stockAfterOrders.quantity).isEqualTo(50)

        // when: 10개 주문을 모두 취소
        orderIds.forEach { orderId ->
            orderService.cancelOrder(orderId, user.id)
        }

        // then: 재고가 100으로 복구되어야 함
        val finalStock = requireNotNull(stockRepository.findByProductId(product.id))
        assertThat(finalStock.quantity).isEqualTo(100)

        // 모든 주문이 CANCELLED 상태여야 함
        orderIds.forEach { orderId ->
            val order = requireNotNull(orderRepository.findById(orderId))
            assertThat(order.status).isEqualTo(OrderStatus.CANCELLED)
        }
    }
}
