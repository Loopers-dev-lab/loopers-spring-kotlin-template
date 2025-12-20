package com.loopers.application.order

import com.loopers.domain.brand.BrandService
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderEvent
import com.loopers.domain.order.OrderResult
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.outbox.OutboxService
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentEvent
import com.loopers.domain.payment.PaymentMethod
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.point.PointService
import com.loopers.domain.product.ProductService
import com.loopers.domain.user.Gender
import com.loopers.domain.user.UserService
import com.loopers.support.fixtures.BrandFixtures.createBrand
import com.loopers.support.fixtures.OrderFixtures.createOrder
import com.loopers.support.fixtures.OrderFixtures.createOrderDetail
import com.loopers.support.fixtures.ProductFixtures.createProduct
import com.loopers.support.fixtures.UserFixtures.createUser
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

@DisplayName("OrderFacade 단위 테스트")
class OrderFacadeTest {

    private val couponService: CouponService = mockk()
    private val orderService: OrderService = mockk()
    private val userService: UserService = mockk()
    private val brandService: BrandService = mockk()
    private val productService: ProductService = mockk()
    private val pointService: PointService = mockk()
    private val paymentService: PaymentService = mockk()
    private val applicationEventPublisher: ApplicationEventPublisher = mockk()
    private val outboxService: OutboxService = mockk()
    private val orderFacade = OrderFacade(couponService, orderService, userService, brandService, productService, pointService, paymentService, applicationEventPublisher, outboxService)

    private val pageable: Pageable = PageRequest.of(0, 20)

    @Nested
    @DisplayName("주문 목록 조회")
    inner class GetOrders {

        @Test
        fun `주문 목록 조회 시 모든 서비스 메서드가 순서대로 호출된다`() {
            // given
            val userId = "1"
            val userIdLong = 1L

            val user = createUser(userIdLong, "userId", "test@example.com", "1990-01-01", Gender.MALE)

            val order1 = createOrder(1L, OrderStatus.PENDING, 10000L, userIdLong)
            val order2 = createOrder(2L, OrderStatus.COMPLETED, 20000L, userIdLong)
            val orders = listOf(order1, order2)
            val orderPage: Page<Order> = PageImpl(orders, pageable, orders.size.toLong())

            every { userService.getMyInfo(userId) } returns user
            every { orderService.getOrders(userIdLong, pageable) } returns orderPage

            // when
            val result = orderFacade.getOrders(userId, pageable)

            // then
            verify(exactly = 1) { userService.getMyInfo(userId) }
            verify(exactly = 1) { orderService.getOrders(userIdLong, pageable) }

            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(2)
                softly.assertThat(result.totalElements).isEqualTo(2)
                softly.assertThat(result.content[0].id).isEqualTo(1L)
                softly.assertThat(result.content[0].status).isEqualTo(OrderStatus.PENDING)
                softly.assertThat(result.content[0].totalAmount).isEqualTo(10000L)
            }
        }

        @Test
        fun `주문이 없으면 빈 페이지를 반환한다`() {
            // given
            val userId = "1"
            val userIdLong = 1L

            val user = createUser(userIdLong, "userId", "test@example.com", "1990-01-01", Gender.MALE)
            val emptyPage: Page<Order> = PageImpl(emptyList(), pageable, 0L)

            every { userService.getMyInfo(userId) } returns user
            every { orderService.getOrders(userIdLong, pageable) } returns emptyPage

            // when
            val result = orderFacade.getOrders(userId, pageable)

            // then
            verify(exactly = 1) { userService.getMyInfo(userId) }
            verify(exactly = 1) { orderService.getOrders(userIdLong, pageable) }

            assertSoftly { softly ->
                softly.assertThat(result.content).isEmpty()
                softly.assertThat(result.totalElements).isEqualTo(0)
            }
        }
    }

    @Nested
    @DisplayName("주문 상세 조회")
    inner class GetOrder {

        @Test
        fun `주문 상세 조회 시 모든 서비스 메서드가 순서대로 호출된다`() {
            // given
            val userId = "1"
            val userIdLong = 1L
            val orderId = 1L
            val brandId = 1L
            val productId = 1L

            val user = createUser(userIdLong, "userId", "test@example.com", "1990-01-01", Gender.MALE)
            val brand = createBrand(brandId, "브랜드A")
            val product = createProduct(productId, "상품1", 10000L, brandId)
            val order = createOrder(orderId, OrderStatus.PENDING, 10000L, userIdLong)
            val orderDetail = createOrderDetail(1L, 2L, brand, product, order)
            val orderDetails = listOf(orderDetail)

            every { userService.getMyInfo(userId) } returns user
            every { orderService.getOrder(userIdLong, orderId) } returns order
            every { orderService.getOrderDetail(orderId) } returns orderDetails

            // when
            val result = orderFacade.getOrder(userId, orderId)

            // then
            verify(exactly = 1) { userService.getMyInfo(userId) }
            verify(exactly = 1) { orderService.getOrder(userIdLong, orderId) }
            verify(exactly = 1) { orderService.getOrderDetail(orderId) }

            assertSoftly { softly ->
                softly.assertThat(result.id).isEqualTo(orderId)
                softly.assertThat(result.status).isEqualTo(OrderStatus.PENDING)
                softly.assertThat(result.totalAmount).isEqualTo(10000L)
                softly.assertThat(result.items).hasSize(1)
                softly.assertThat(result.items[0].brandName).isEqualTo("브랜드A")
                softly.assertThat(result.items[0].productName).isEqualTo("상품1")
            }
        }
    }

    @Nested
    @DisplayName("주문 생성")
    inner class PlaceOrder {

        @Test
        fun `포인트 결제로 주문 생성 시 모든 서비스가 올바른 순서로 호출된다`() {
            // given
            val userId = "user123"
            val userIdLong = 1L
            val orderId = 1L
            val couponId = 100L

            val user = createUser(id = userIdLong)
            val brand = createBrand(id = 1L)
            val product = createProduct(id = 1L, brandId = 1L)
            val order = createOrder(id = orderId)
            val orderDetail = createOrderDetail(1L, 1L, brand, product, order)
            val orderResult = OrderResult.Create(order = order, orderDetails = listOf(orderDetail))
            val items = listOf(OrderCommand.OrderDetailCommand(productId = 1L, quantity = 1))
            val totalAmount = 10000L
            val discountAmount = 1000L
            val finalTotalAmount = 9000L

            every { userService.getMyInfo(userId) } returns user
            every { productService.getProducts(any()) } returns listOf(product)
            justRun { productService.validateProductsExist(any(), any()) }
            justRun { productService.validateStockAvailability(any()) }
            every { brandService.getAllBrand(any()) } returns listOf(brand)
            every { orderService.calculateTotalAmount(any(), any()) } returns totalAmount
            every { couponService.calculateCouponDiscount(any(), any(), any()) } returns discountAmount
            justRun { pointService.use(any(), any()) }
            justRun { productService.deductAllStock(any()) }
            every { orderService.createOrder(any()) } returns orderResult
            justRun { applicationEventPublisher.publishEvent(any<OrderEvent.OrderCreated>()) }
            justRun {
                outboxService.save(
                    aggregateType = any(),
                    aggregateId = any(),
                    eventType = any(),
                    payload = any(),
                )
            }

            // when
            orderFacade.placeOrder(
                OrderCommand.Place(
                    userId = userId,
                    couponId = couponId,
                    items = items,
                    paymentMethod = PaymentMethod.POINT,
                ),
            )

            // then - 호출 순서 검증
            verify(exactly = 1) { userService.getMyInfo(userId) }
            verify(exactly = 1) { productService.getProducts(any()) }
            verify(exactly = 1) { productService.validateProductsExist(any(), any()) }
            verify(exactly = 1) { productService.validateStockAvailability(any()) }
            verify(exactly = 1) { brandService.getAllBrand(any()) }
            verify(exactly = 1) { orderService.calculateTotalAmount(any(), any()) }
            verify(exactly = 1) { couponService.calculateCouponDiscount(userIdLong, couponId, totalAmount) }
            verify(exactly = 1) { pointService.use(userIdLong, finalTotalAmount) }
            verify(exactly = 1) { productService.deductAllStock(any()) }
            verify(exactly = 1) { orderService.createOrder(any()) }
        }

        @Test
        fun `카드 결제로 주문 생성 시 모든 서비스가 올바른 순서로 호출된다`() {
            // given
            val userId = "user123"
            val userIdLong = 1L
            val orderId = 1L
            val couponId = 100L
            val cardType = CardType.KB
            val cardNo = "1234-5678-9814-1451"

            val user = createUser(id = userIdLong)
            val brand = createBrand(id = 1L)
            val product = createProduct(id = 1L, brandId = 1L)
            val order = createOrder(id = orderId)
            val orderDetail = createOrderDetail(1L, 1L, brand, product, order)
            val orderResult = OrderResult.Create(order = order, orderDetails = listOf(orderDetail))
            val payment = mockk<com.loopers.domain.payment.Payment>(relaxed = true)
            val items = listOf(OrderCommand.OrderDetailCommand(productId = 1L, quantity = 1))
            val totalAmount = 10000L
            val discountAmount = 1000L

            every { userService.getMyInfo(userId) } returns user
            every { productService.getProducts(any()) } returns listOf(product)
            justRun { productService.validateProductsExist(any(), any()) }
            justRun { productService.validateStockAvailability(any()) }
            every { brandService.getAllBrand(any()) } returns listOf(brand)
            every { orderService.calculateTotalAmount(any(), any()) } returns totalAmount
            every { couponService.calculateCouponDiscount(any(), any(), any()) } returns discountAmount
            every { orderService.createOrder(any()) } returns orderResult
            every { paymentService.create(any()) } returns payment
            every { payment.id } returns 1L
            justRun { applicationEventPublisher.publishEvent(any<PaymentEvent.PaymentRequest>()) }
            justRun { applicationEventPublisher.publishEvent(any<OrderEvent.OrderCreated>()) }

            // when
            orderFacade.placeOrder(
                OrderCommand.Place(
                    userId = userId,
                    couponId = couponId,
                    items = items,
                    paymentMethod = PaymentMethod.CARD,
                    cardType = cardType,
                    cardNo = cardNo,
                ),
            )

            // then - 호출 순서 검증
            verify(exactly = 1) { userService.getMyInfo(userId) }
            verify(exactly = 1) { productService.getProducts(any()) }
            verify(exactly = 1) { productService.validateProductsExist(any(), any()) }
            verify(exactly = 1) { productService.validateStockAvailability(any()) }
            verify(exactly = 1) { brandService.getAllBrand(any()) }
            verify(exactly = 1) { orderService.calculateTotalAmount(any(), any()) }
            verify(exactly = 1) { couponService.calculateCouponDiscount(userIdLong, couponId, totalAmount) }
            verify(exactly = 1) { orderService.createOrder(any()) }
            verify(exactly = 1) { paymentService.create(any()) }
            // PG 결제 요청은 이벤트로 처리됨
            verify(exactly = 1) { applicationEventPublisher.publishEvent(ofType<PaymentEvent.PaymentRequest>()) }
            verify(exactly = 1) { applicationEventPublisher.publishEvent(ofType<OrderEvent.OrderCreated>()) }
            // 카드 결제는 재고 차감이 콜백에서 발생하므로 여기서는 호출되지 않음
            verify(exactly = 0) { productService.deductAllStock(any()) }
        }
    }
}
