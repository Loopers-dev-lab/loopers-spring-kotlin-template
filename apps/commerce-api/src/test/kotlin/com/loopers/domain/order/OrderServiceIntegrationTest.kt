package com.loopers.domain.order

import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents

/**
 * OrderService 통합 테스트
 *
 * 검증 범위:
 * - 주문 생성 오케스트레이션 (Order + OrderItem 저장)
 * - 트랜잭션 원자성
 * - 도메인 이벤트 발행 (OrderCreatedEventV1, OrderCanceledEventV1)
 */
@SpringBootTest
@RecordApplicationEvents
@DisplayName("OrderService 통합 테스트")
class OrderServiceIntegrationTest @Autowired constructor(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("place")
    inner class Place {

        @Test
        @DisplayName("주문을 생성하면 PLACED 상태로 저장된다")
        fun `creates order with PLACED status`() {
            // given
            val product = createProduct()
            val command = placeOrderCommand(userId = 1L, productId = product.id)

            // when
            val savedOrder = orderService.place(command)

            // then
            val foundOrder = orderRepository.findById(savedOrder.id)
            assertThat(foundOrder).isNotNull
            assertThat(foundOrder!!.status).isEqualTo(OrderStatus.PLACED)
        }

        @Test
        @DisplayName("여러 상품을 포함한 주문을 생성할 수 있다")
        fun `creates order with multiple items`() {
            // given
            val product1 = createProduct()
            val product2 = createProduct()
            val product3 = createProduct()
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                items = listOf(
                    placeOrderItem(productId = product1.id),
                    placeOrderItem(productId = product2.id),
                    placeOrderItem(productId = product3.id),
                ),
            )

            // when
            val savedOrder = orderService.place(command)

            // then
            val foundOrder = orderRepository.findById(savedOrder.id)
            assertThat(foundOrder).isNotNull
            assertThat(foundOrder!!.orderItems).hasSize(3)
        }

        @Test
        @DisplayName("주문 생성 시 OrderCreatedEventV1을 발행한다")
        fun `publishes OrderCreatedEventV1 when order is placed`() {
            // given
            val product1 = createProduct()
            val product2 = createProduct()
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                items = listOf(
                    placeOrderItem(productId = product1.id, quantity = 2),
                    placeOrderItem(productId = product2.id, quantity = 3),
                ),
            )

            // when
            val savedOrder = orderService.place(command)

            // then
            val events = applicationEvents.stream(OrderCreatedEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.orderId).isEqualTo(savedOrder.id)
            assertThat(event.orderItems).hasSize(2)
            assertThat(event.orderItems[0].productId).isEqualTo(product1.id)
            assertThat(event.orderItems[0].quantity).isEqualTo(2)
            assertThat(event.orderItems[1].productId).isEqualTo(product2.id)
            assertThat(event.orderItems[1].quantity).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("completePayment")
    inner class CompletePayment {

        @Test
        @DisplayName("결제 완료 시 OrderPaidEventV1을 발행한다")
        fun `publishes OrderPaidEventV1 when payment is completed`() {
            // given
            val product1 = createProduct()
            val product2 = createProduct()
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                items = listOf(
                    placeOrderItem(productId = product1.id, quantity = 2),
                    placeOrderItem(productId = product2.id, quantity = 3),
                ),
            )
            val savedOrder = orderService.place(command)

            // Clear events from place() to focus on completePayment() events
            applicationEvents.clear()

            // when
            val paidOrder = orderService.completePayment(savedOrder.id)

            // then
            val events = applicationEvents.stream(OrderPaidEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.orderId).isEqualTo(paidOrder.id)
            assertThat(event.userId).isEqualTo(1L)
            assertThat(event.orderItems).hasSize(2)
            assertThat(event.orderItems[0].productId).isEqualTo(product1.id)
            assertThat(event.orderItems[0].quantity).isEqualTo(2)
            assertThat(event.orderItems[0].unitPrice).isEqualTo(10000L)
            assertThat(event.orderItems[1].productId).isEqualTo(product2.id)
            assertThat(event.orderItems[1].quantity).isEqualTo(3)
            assertThat(event.orderItems[1].unitPrice).isEqualTo(10000L)
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    inner class CancelOrder {

        @Test
        @DisplayName("주문 취소 시 OrderCanceledEventV1을 발행한다")
        fun `publishes OrderCanceledEventV1 when order is canceled`() {
            // given
            val product1 = createProduct()
            val product2 = createProduct()
            val command = OrderCommand.PlaceOrder(
                userId = 1L,
                items = listOf(
                    placeOrderItem(productId = product1.id, quantity = 2),
                    placeOrderItem(productId = product2.id, quantity = 3),
                ),
            )
            val savedOrder = orderService.place(command)

            // Clear events from place() to focus on cancelOrder() events
            applicationEvents.clear()

            // when
            val canceledOrder = orderService.cancelOrder(savedOrder.id)

            // then
            val events = applicationEvents.stream(OrderCanceledEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.orderId).isEqualTo(canceledOrder.id)
            assertThat(event.orderItems).hasSize(2)
            assertThat(event.orderItems[0].productId).isEqualTo(product1.id)
            assertThat(event.orderItems[0].quantity).isEqualTo(2)
            assertThat(event.orderItems[1].productId).isEqualTo(product2.id)
            assertThat(event.orderItems[1].quantity).isEqualTo(3)
        }
    }

    private fun placeOrderCommand(userId: Long = 1L, productId: Long) = OrderCommand.PlaceOrder(
        userId = userId,
        items = listOf(placeOrderItem(productId = productId)),
    )

    private fun placeOrderItem(
        productId: Long = 1L,
        quantity: Int = 1,
        currentPrice: Money = Money.krw(10000),
    ) = OrderCommand.PlaceOrderItem(
        productId = productId,
        productName = "테스트 상품",
        quantity = quantity,
        currentPrice = currentPrice,
    )

    private fun createProduct(
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
        val brand = brandRepository.save(Brand.create("테스트 브랜드"))
        val product = Product.create(name = "테스트 상품", price = price, brand = brand)
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }
}
