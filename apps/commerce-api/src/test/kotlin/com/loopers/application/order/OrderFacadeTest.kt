package com.loopers.application.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderDetail
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.Product
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.util.withCreatedAt
import com.loopers.support.util.withId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

@DisplayName("OrderFacade 단위 테스트")
class OrderFacadeTest {

    private val orderService: OrderService = mockk()
    private val userService: UserService = mockk()
    private val orderFacade = OrderFacade(orderService, userService)

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

        @Test
        fun `존재하지 않는 사용자는 예외를 발생시킨다`() {
            // given
            val userId = "999"

            every { userService.getMyInfo(userId) } returns null

            // when & then
            assertThatThrownBy { orderFacade.getOrders(userId, pageable) }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("유저를 찾을 수 없습니다")
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

        @Test
        fun `존재하지 않는 사용자는 예외를 발생시킨다`() {
            // given
            val userId = "999"
            val orderId = 1L

            every { userService.getMyInfo(userId) } returns null

            // when & then
            assertThatThrownBy { orderFacade.getOrder(userId, orderId) }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("유저를 찾을 수 없습니다")

            verify(exactly = 1) { userService.getMyInfo(userId) }
            verify(exactly = 0) { orderService.getOrder(any(), any()) }
            verify(exactly = 0) { orderService.getOrderDetail(any()) }
        }
    }

    private fun createUser(
        id: Long,
        userId: String,
        email: String,
        birthDate: String,
        gender: Gender,
    ): User {
        return User.create(
            UserCommand.SignUp(
                userId = userId,
                email = email,
                birthDate = birthDate,
                gender = gender,
            ),
        ).withId(id)
    }

    private fun createBrand(
        id: Long,
        name: String,
    ): Brand {
        return Brand.create(name = name)
            .withId(id)
            .withCreatedAt(ZonedDateTime.now())
    }

    private fun createProduct(
        id: Long,
        name: String,
        price: Long,
        brandId: Long,
    ): Product {
        return Product.create(
            name = name,
            price = price,
            brandId = brandId,
        ).withId(id).withCreatedAt(ZonedDateTime.now())
    }

    private fun createOrder(
        id: Long,
        status: OrderStatus,
        totalAmount: Long,
        userId: Long,
    ): Order {
        return Order.create(
            totalAmount = totalAmount,
            userId = userId,
        ).apply {
            when (status) {
                OrderStatus.COMPLETED -> complete()
                OrderStatus.CANCELLED -> cancel()
                else -> {}
            }
        }.withId(id).withCreatedAt(ZonedDateTime.now())
    }

    private fun createOrderDetail(
        id: Long,
        quantity: Long,
        brand: Brand,
        product: Product,
        order: Order,
    ): OrderDetail {
        return OrderDetail.create(
            quantity = quantity,
            brand = brand,
            product = product,
            order = order,
        ).withId(id).withCreatedAt(ZonedDateTime.now())
    }
}
