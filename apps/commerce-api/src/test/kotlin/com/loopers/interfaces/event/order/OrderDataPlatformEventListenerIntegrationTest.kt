package com.loopers.interfaces.event.order

import com.loopers.domain.like.LikeDataPlatformClient
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderDataPlatformClient
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.PaymentPaidEventV1
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThatCode
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OrderDataPlatformEventListener 통합 테스트
 *
 * 검증 범위:
 * - PaymentPaidEventV1 -> OrderDataPlatformClient.sendOrderCompleted 호출 (AFTER_COMMIT, 비동기)
 *
 * 참고: DataPlatformClientAdapter는 현재 stub 구현이므로 실제 HTTP 호출이 없습니다.
 * 따라서 호출 여부를 상태로 검증하기 위해 AtomicBoolean 플래그를 사용합니다.
 */
@SpringBootTest
@DisplayName("OrderDataPlatformEventListener 통합 테스트")
class OrderDataPlatformEventListenerIntegrationTest @Autowired constructor(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val orderRepository: OrderRepository,
) {
    @MockkBean
    private lateinit var orderDataPlatformClient: OrderDataPlatformClient

    @MockkBean
    private lateinit var likeDataPlatformClient: LikeDataPlatformClient

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    @DisplayName("PaymentPaidEventV1 발행 시 OrderDataPlatformClient.sendOrderCompleted가 호출된다")
    fun `PaymentPaidEventV1 triggers sendOrderCompleted call`() {
        // given
        val userId = 1L
        val order = createOrderWithItems(userId)
        val orderId = order.id
        val paymentId = 100L
        val event = PaymentPaidEventV1(
            paymentId = paymentId,
            orderId = orderId,
        )

        val wasCalled = AtomicBoolean(false)
        every { orderDataPlatformClient.sendOrderCompleted(orderId) } answers {
            wasCalled.set(true)
            true
        }

        // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
        transactionTemplate.execute {
            applicationEventPublisher.publishEvent(event)
        }

        // then - 비동기 처리이므로 Awaitility 사용, 상태 검증
        await().atMost(Duration.ofSeconds(5)).untilTrue(wasCalled)
    }

    @Test
    @DisplayName("PaymentPaidEventV1 발행 시 sendOrderCompleted가 실패해도 예외가 전파되지 않는다")
    fun `PaymentPaidEventV1 handles exception gracefully`() {
        // given
        val userId = 1L
        val order = createOrderWithItems(userId)
        val orderId = order.id
        val paymentId = 200L
        val event = PaymentPaidEventV1(
            paymentId = paymentId,
            orderId = orderId,
        )

        val wasCalled = AtomicBoolean(false)
        every { orderDataPlatformClient.sendOrderCompleted(orderId) } answers {
            wasCalled.set(true)
            throw RuntimeException("External service error")
        }

        // when & then - 예외가 전파되지 않아야 함
        assertThatCode {
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }
        }.doesNotThrowAnyException()

        // then - 비동기 처리 완료 대기 및 호출 여부 상태 검증
        await().atMost(Duration.ofSeconds(5)).untilTrue(wasCalled)
    }

    private fun createProduct(
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(name = "테스트 상품", price = price, brand = brand)
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        return savedProduct
    }

    private fun createOrderWithItems(
        userId: Long,
        product: Product = createProduct(),
        quantity: Int = 1,
    ): Order {
        val order = Order.place(userId)
        order.addOrderItem(
            productId = product.id,
            quantity = quantity,
            productName = product.name,
            unitPrice = product.price,
        )
        return orderRepository.save(order)
    }
}
