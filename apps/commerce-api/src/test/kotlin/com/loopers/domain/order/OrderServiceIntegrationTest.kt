package com.loopers.domain.order

import com.loopers.application.order.OrderCreateRequest
import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderItemRequest
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.point.Point
import com.loopers.domain.point.PointRepository
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Transactional
class OrderServiceIntegrationTest {
    @Autowired
    private lateinit var orderFacade: OrderFacade

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var brandRepository: BrandRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var stockRepository: StockRepository

    @Autowired
    private lateinit var pointRepository: PointRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var orderService: OrderService

    private lateinit var user: User
    private lateinit var brand: Brand
    private lateinit var product1: Product
    private lateinit var product2: Product

    @BeforeEach
    fun setUp() {
        // 사용자 생성
        user = User(
            name = "홍길동",
            email = "hong@example.com",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 1),
        )
        user = userRepository.save(user)

        // 포인트 생성
        val point = Point(
            userId = user.id,
            balance = Money(BigDecimal("1000000"), Currency.KRW),
        )
        pointRepository.save(point)

        // 브랜드 생성
        brand = Brand(name = "통합테스트브랜드", description = "테스트용 브랜드")
        brand = brandRepository.save(brand)

        // 상품 생성
        product1 = Product(
            name = "통합테스트상품1",
            price = Price(BigDecimal("100000"), Currency.KRW),
            brand = brand,
        )
        product1 = productRepository.save(product1)

        product2 = Product(
            name = "통합테스트상품2",
            price = Price(BigDecimal("50000"), Currency.KRW),
            brand = brand,
        )
        product2 = productRepository.save(product2)

        // 재고 생성
        stockRepository.save(Stock(productId = product1.id, quantity = 100))
        stockRepository.save(Stock(productId = product2.id, quantity = 100))
    }

    @Test
    fun `정상적으로 주문을 생성하고 재고와 포인트가 차감된다`() {
        // given
        val request = OrderCreateRequest(
            items = listOf(
                OrderItemRequest(productId = product1.id, quantity = 2),
                OrderItemRequest(productId = product2.id, quantity = 1),
            ),
        )

        // when
        val orderInfo = orderFacade.createOrder(user.id, request)

        // then
        assertThat(orderInfo.userId).isEqualTo(user.id)
        assertThat(orderInfo.totalAmount).isEqualTo(BigDecimal("250000")) // 200000 + 50000

        val savedOrder = orderRepository.findById(orderInfo.orderId)!!
        assertThat(savedOrder.items).hasSize(2)

        // 재고 확인
        val stock1 = stockRepository.findByProductId(product1.id)!!
        val stock2 = stockRepository.findByProductId(product2.id)!!
        assertThat(stock1.quantity).isEqualTo(98) // 100 - 2
        assertThat(stock2.quantity).isEqualTo(99) // 100 - 1

        // 포인트 확인
        val point = pointRepository.findByUserId(user.id)!!
        assertThat(point.balance.amount).isEqualTo(BigDecimal("750000")) // 1000000 - 250000
    }

    @Test
    fun `재고가 부족하면 주문이 실패하고 트랜잭션이 롤백된다`() {
        // given
        val request = OrderCreateRequest(
            items = listOf(
                // 재고 부족
                OrderItemRequest(productId = product1.id, quantity = 101),
            ),
        )

        val initialPoint = pointRepository.findByUserId(user.id)!!.balance.amount

        // when & then
        assertThatThrownBy {
            orderFacade.createOrder(user.id, request)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("재고 부족")

        // 재고는 차감되지 않아야 함
        val stock = stockRepository.findByProductId(product1.id)!!
        assertThat(stock.quantity).isEqualTo(100)

        // 포인트도 차감되지 않아야 함
        val point = pointRepository.findByUserId(user.id)!!
        assertThat(point.balance.amount).isEqualTo(initialPoint)
    }

    @Test
    fun `포인트가 부족하면 주문이 실패하고 트랜잭션이 롤백된다`() {
        // given
        // 포인트를 적게 설정
        val point = pointRepository.findByUserIdWithLock(user.id)!!
        point.deduct(Money(BigDecimal("950000"), Currency.KRW))
        pointRepository.save(point)

        val request = OrderCreateRequest(
            items = listOf(
                // 100,000원
                OrderItemRequest(productId = product1.id, quantity = 1),
            ),
        )

        // when & then
        assertThatThrownBy {
            orderFacade.createOrder(user.id, request)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("포인트 부족")

        // 재고는 차감되지 않아야 함
        val stock = stockRepository.findByProductId(product1.id)!!
        assertThat(stock.quantity).isEqualTo(100)
    }

    @Test
    fun `주문 항목에 상품 스냅샷이 저장된다`() {
        // given
        val request = OrderCreateRequest(
            items = listOf(
                OrderItemRequest(productId = product1.id, quantity = 1),
            ),
        )

        // when
        val orderInfo = orderFacade.createOrder(user.id, request)

        // 상품 가격 변경
        product1.updatePrice(Price(BigDecimal("200000"), Currency.KRW))
        productRepository.save(product1)

        // then
        val savedOrder = orderRepository.findById(orderInfo.orderId)!!
        assertThat(savedOrder.items[0].productName).isEqualTo("통합테스트상품1")
        assertThat(savedOrder.items[0].priceAtOrder.amount).isEqualTo(BigDecimal("100000")) // 주문 당시 가격
        assertThat(savedOrder.items[0].brandName).isEqualTo("통합테스트브랜드")
    }

    @Test
    fun `주문을 취소하면 재고가 복구된다`() {
        // given
        val request = OrderCreateRequest(
            items = listOf(
                OrderItemRequest(productId = product1.id, quantity = 2),
                OrderItemRequest(productId = product2.id, quantity = 3),
            ),
        )
        val orderInfo = orderFacade.createOrder(user.id, request)

        // 재고 차감 확인
        val stockAfterOrder1 = stockRepository.findByProductId(product1.id)!!
        val stockAfterOrder2 = stockRepository.findByProductId(product2.id)!!
        assertThat(stockAfterOrder1.quantity).isEqualTo(98) // 100 - 2
        assertThat(stockAfterOrder2.quantity).isEqualTo(97) // 100 - 3

        // when
        orderService.cancelOrder(orderInfo.orderId, user.id)

        // then
        val cancelledOrder = orderRepository.findById(orderInfo.orderId)!!
        assertThat(cancelledOrder.status).isEqualTo(OrderStatus.CANCELLED)

        // 재고 복구 확인
        val stockAfterCancel1 = stockRepository.findByProductId(product1.id)!!
        val stockAfterCancel2 = stockRepository.findByProductId(product2.id)!!
        assertThat(stockAfterCancel1.quantity).isEqualTo(100) // 98 + 2
        assertThat(stockAfterCancel2.quantity).isEqualTo(100) // 97 + 3
    }

    @Test
    fun `주문 취소 시 본인의 주문만 취소할 수 있다`() {
        // given
        val request = OrderCreateRequest(
            items = listOf(
                OrderItemRequest(productId = product1.id, quantity = 1),
            ),
        )
        val orderInfo = orderFacade.createOrder(user.id, request)

        // 다른 사용자 생성
        val otherUser = User(
            name = "김철수",
            email = "kim@example.com",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1995, 5, 5),
        )
        val savedOtherUser = userRepository.save(otherUser)

        // when & then
        assertThatThrownBy {
            orderService.cancelOrder(orderInfo.orderId, savedOtherUser.id)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("본인의 주문만 취소할 수 있습니다")

        // 주문 상태는 변경되지 않아야 함
        val order = orderRepository.findById(orderInfo.orderId)!!
        assertThat(order.status).isEqualTo(OrderStatus.PENDING)

        // 재고도 복구되지 않아야 함
        val stock = stockRepository.findByProductId(product1.id)!!
        assertThat(stock.quantity).isEqualTo(99) // 100 - 1 (여전히 차감된 상태)
    }

    @Test
    fun `이미 확정된 주문은 취소할 수 없다`() {
        // given
        val request = OrderCreateRequest(
            items = listOf(
                OrderItemRequest(productId = product1.id, quantity = 2),
            ),
        )
        val orderInfo = orderFacade.createOrder(user.id, request)

        // 주문 확정
        val order = orderRepository.findById(orderInfo.orderId)!!
        order.confirm()
        orderRepository.save(order)

        // when & then
        assertThatThrownBy {
            orderService.cancelOrder(orderInfo.orderId, user.id)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("이미 확정된 주문은 취소할 수 없습니다")

        // 재고는 복구되지 않아야 함
        val stock = stockRepository.findByProductId(product1.id)!!
        assertThat(stock.quantity).isEqualTo(98) // 100 - 2 (여전히 차감된 상태)
    }
}
