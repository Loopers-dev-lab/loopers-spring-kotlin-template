package com.loopers.interfaces.event.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.payment.PaymentPaidEventV1
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.TimeUnit

/**
 * OrderEventListener E2E 통합 테스트
 *
 * 검증 범위:
 * - PaymentPaidEventV1 -> 주문 완료 (AFTER_COMMIT, 비동기)
 * - PaymentFailedEventV1 -> 주문 취소 (BEFORE_COMMIT, 동기)
 *
 * Note: 이벤트 체인을 통한 재고 복구는 ProductEventListenerIntegrationTest에서 검증합니다.
 * OrderEventListener는 주문 취소만 담당하며, OrderCanceledEventV1 발행 후
 * ProductEventListener가 재고를 복구합니다.
 */
@SpringBootTest
@DisplayName("OrderEventListener 통합 테스트")
class OrderEventListenerIntegrationTest @Autowired constructor(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val orderRepository: OrderRepository,
    private val stockRepository: StockRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("onPaymentPaid")
    inner class OnPaymentPaid {

        @Test
        @DisplayName("PaymentPaidEventV1 발행 시 주문 상태가 PAID로 변경된다")
        fun `PaymentPaidEventV1 triggers order completion`() {
            // given
            val userId = 1L
            val order = createOrderWithItems(userId)
            assertThat(order.status).isEqualTo(OrderStatus.PLACED)

            val event = PaymentPaidEventV1(
                paymentId = 1L,
                orderId = order.id,
            )

            // when - AFTER_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 비동기 처리이므로 Awaitility 사용
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val updatedOrder = orderRepository.findById(order.id)!!
                assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAID)
            }
        }
    }

    @Nested
    @DisplayName("onPaymentFailed")
    inner class OnPaymentFailed {

        @Test
        @DisplayName("PaymentFailedEventV1 발행 시 주문 상태가 CANCELLED로 변경된다")
        fun `PaymentFailedEventV1 triggers order cancellation`() {
            // given
            val userId = 1L
            val order = createOrderWithItems(userId)
            assertThat(order.status).isEqualTo(OrderStatus.PLACED)

            val event = PaymentFailedEventV1(
                paymentId = 1L,
                orderId = order.id,
                userId = userId,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
            )

            // when - BEFORE_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 동기 처리이므로 즉시 확인 가능
            val canceledOrder = orderRepository.findById(order.id)!!
            assertThat(canceledOrder.status).isEqualTo(OrderStatus.CANCELLED)
        }
    }

    // ===========================================
    // 도메인 픽스처 헬퍼
    // ===========================================

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
