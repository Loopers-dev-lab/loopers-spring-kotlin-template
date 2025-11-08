package com.loopers.domain.order

import com.loopers.domain.point.Point
import com.loopers.domain.point.PointRepository
import com.loopers.domain.product.*
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderServiceTest {
    private val orderRepository: OrderRepository = mockk(relaxed = true)
    private val productRepository: ProductRepository = mockk()
    private val stockRepository: StockRepository = mockk()
    private val pointRepository: PointRepository = mockk()

    private val orderService = OrderService(
        orderRepository,
        productRepository,
        stockRepository,
        pointRepository
    )

    private fun createTestProduct(id: Long, name: String, price: BigDecimal): Product {
        return Product(
            name = name,
            price = Price(price, Currency.KRW),
            brand = mockk {
                every { this@mockk.id } returns 1L
                every { this@mockk.name } returns "Test Brand"
                every { this@mockk.description } returns "Test Description"
            }
        ).apply {
            // Reflection으로 id 설정
            val idField = Product::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }
    }

    @Test
    fun `정상적으로 주문을 생성할 수 있다`() {
        // given
        val userId = 1L
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"))
        val stock = Stock(productId = 100L, quantity = 100)
        val point = Point(userId = userId, balance = Money(BigDecimal("500000"), Currency.KRW))

        val orderItemRequests = listOf(
            OrderItemRequest(productId = 100L, quantity = 2)
        )

        every { productRepository.findById(100L) } returns product
        every { stockRepository.findByProductId(100L) } returns stock
        every { pointRepository.findByUserId(userId) } returns point
        every { stockRepository.findByProductIdWithLock(100L) } returns stock
        every { pointRepository.findByUserIdWithLock(userId) } returns point
        every { orderRepository.save(any()) } answers { firstArg() }
        every { stockRepository.save(any()) } answers { firstArg() }
        every { pointRepository.save(any()) } answers { firstArg() }

        // when
        val order = orderService.createOrder(userId, orderItemRequests)

        // then
        assertThat(order.userId).isEqualTo(userId)
        assertThat(order.items).hasSize(1)
        assertThat(order.items[0].quantity).isEqualTo(2)
        verify { orderRepository.save(any()) }
        verify { stockRepository.save(any()) }
        verify { pointRepository.save(any()) }
    }

    @Test
    fun `존재하지 않는 상품 주문 시 예외가 발생한다`() {
        // given
        val userId = 1L
        val orderItemRequests = listOf(
            OrderItemRequest(productId = 999L, quantity = 1)
        )

        every { productRepository.findById(999L) } returns null

        // when & then
        assertThatThrownBy {
            orderService.createOrder(userId, orderItemRequests)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품을 찾을 수 없습니다")
    }

    @Test
    fun `재고가 부족하면 예외가 발생한다`() {
        // given
        val userId = 1L
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"))
        val stock = Stock(productId = 100L, quantity = 5)

        val orderItemRequests = listOf(
            OrderItemRequest(productId = 100L, quantity = 10)
        )

        every { productRepository.findById(100L) } returns product
        every { stockRepository.findByProductId(100L) } returns stock

        // when & then
        assertThatThrownBy {
            orderService.createOrder(userId, orderItemRequests)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("재고 부족")
    }

    @Test
    fun `포인트가 부족하면 예외가 발생한다`() {
        // given
        val userId = 1L
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"))
        val stock = Stock(productId = 100L, quantity = 100)
        val point = Point(userId = userId, balance = Money(BigDecimal("50000"), Currency.KRW))

        val orderItemRequests = listOf(
            OrderItemRequest(productId = 100L, quantity = 2) // 총 200,000원
        )

        every { productRepository.findById(100L) } returns product
        every { stockRepository.findByProductId(100L) } returns stock
        every { pointRepository.findByUserId(userId) } returns point

        // when & then
        assertThatThrownBy {
            orderService.createOrder(userId, orderItemRequests)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("포인트 부족")
    }

    @Test
    fun `주문 시 재고 정보가 없으면 예외가 발생한다`() {
        // given
        val userId = 1L
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"))

        val orderItemRequests = listOf(
            OrderItemRequest(productId = 100L, quantity = 1)
        )

        every { productRepository.findById(100L) } returns product
        every { stockRepository.findByProductId(100L) } returns null

        // when & then
        assertThatThrownBy {
            orderService.createOrder(userId, orderItemRequests)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("재고 정보를 찾을 수 없습니다")
    }

    @Test
    fun `주문 시 포인트 정보가 없으면 예외가 발생한다`() {
        // given
        val userId = 1L
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"))
        val stock = Stock(productId = 100L, quantity = 100)

        val orderItemRequests = listOf(
            OrderItemRequest(productId = 100L, quantity = 1)
        )

        every { productRepository.findById(100L) } returns product
        every { stockRepository.findByProductId(100L) } returns stock
        every { pointRepository.findByUserId(userId) } returns null

        // when & then
        assertThatThrownBy {
            orderService.createOrder(userId, orderItemRequests)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("포인트 정보를 찾을 수 없습니다")
    }

    @Test
    fun `여러 상품을 주문할 수 있다`() {
        // given
        val userId = 1L
        val product1 = createTestProduct(100L, "운동화", BigDecimal("100000"))
        val product2 = createTestProduct(101L, "티셔츠", BigDecimal("50000"))
        val stock1 = Stock(productId = 100L, quantity = 100)
        val stock2 = Stock(productId = 101L, quantity = 100)
        val point = Point(userId = userId, balance = Money(BigDecimal("500000"), Currency.KRW))

        val orderItemRequests = listOf(
            OrderItemRequest(productId = 100L, quantity = 1),
            OrderItemRequest(productId = 101L, quantity = 2)
        )

        every { productRepository.findById(100L) } returns product1
        every { productRepository.findById(101L) } returns product2
        every { stockRepository.findByProductId(100L) } returns stock1
        every { stockRepository.findByProductId(101L) } returns stock2
        every { pointRepository.findByUserId(userId) } returns point
        every { stockRepository.findByProductIdWithLock(100L) } returns stock1
        every { stockRepository.findByProductIdWithLock(101L) } returns stock2
        every { pointRepository.findByUserIdWithLock(userId) } returns point
        every { orderRepository.save(any()) } answers { firstArg() }
        every { stockRepository.save(any()) } answers { firstArg() }
        every { pointRepository.save(any()) } answers { firstArg() }

        // when
        val order = orderService.createOrder(userId, orderItemRequests)

        // then
        assertThat(order.items).hasSize(2)
        assertThat(order.calculateTotalAmount().amount).isEqualTo(BigDecimal("200000")) // 100000 + 100000
    }
}
