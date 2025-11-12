package com.loopers.domain.order

import com.loopers.IntegrationTest
import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand
import com.loopers.domain.user.Gender
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.order.OrderDetailJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import kotlin.jvm.java

class OrderServiceTest() : IntegrationTest() {

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var brandJpaRepository: BrandJpaRepository

    @Autowired
    private lateinit var productJpaRepository: ProductJpaRepository

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var orderJpaRepository: OrderJpaRepository

    @Autowired
    private lateinit var orderDetailJpaRepository: OrderDetailJpaRepository

    @Nested
    @DisplayName("getOrders 메서드는")
    inner class GetOrders {
        @Test
        fun `getOrders - 유저의 주문 목록을 조회한다`() {
            val user = createUser()
            val order1 = orderJpaRepository.save(Order.create(1000, user.id))
            val order2 = orderJpaRepository.save(Order.create(2000, user.id))

            val result = orderService.getOrders(user.id, PageRequest.of(0, 10))

            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(2)
                softly.assertThat(result.content.map { it.id }).containsExactlyInAnyOrder(order1.id, order2.id)
            }
        }
    }

    @Nested
    @DisplayName("getOrder 메서드는")
    inner class GetOrder {

        @Test
        fun `주문을 정상 조회한다`() {
            val user = createUser()
            val order = orderJpaRepository.save(Order.create(5000, user.id))

            val result = orderService.getOrder(order.id, user.id)

            assertSoftly { softly ->
                softly.assertThat(result.id).isEqualTo(order.id)
                softly.assertThat(result.totalAmount).isEqualTo(5000)
            }
        }

        @Test
        fun `존재하지 않는 주문이면 예외를 던진다`() {
            val user = createUser()

            assertThatThrownBy { orderService.getOrder(999L, user.id) }
                .isInstanceOf(CoreException::class.java)
        }

        @Test
        fun `다른 사용자의 주문이면 예외를 던진다`() {
            val user1 = createUser()
            val user2 = createUser("user1234", "other@example.com")
            val order = orderJpaRepository.save(Order.create(1000, user1.id))

            assertThatThrownBy { orderService.getOrder(order.id, user2.id) }
                .isInstanceOf(CoreException::class.java)
        }
    }

    @Nested
    @DisplayName("getOrderDetail 메서드는")
    inner class GetOrderDetail {
        @Test
        fun `특정 주문의 상세정보를 조회한다`() {
            val user = createUser()
            val brand = createBrand()
            val product = createProduct(brand.id)
            val order = orderJpaRepository.save(Order.create(3000, user.id))
            val orderDetail = orderDetailJpaRepository.save(
                OrderDetail.create(2, brand, product, order),
            )

            val result = orderService.getOrderDetail(order.id)

            assertSoftly { softly ->
                softly.assertThat(result).hasSize(1)
                softly.assertThat(result.first().id).isEqualTo(orderDetail.id)
                softly.assertThat(result.first().productId).isEqualTo(orderDetail.productId)
                softly.assertThat(result.first().brandId).isEqualTo(orderDetail.brandId)
                softly.assertThat(result.first().quantity).isEqualTo(orderDetail.quantity)
            }
        }
    }

    @Nested
    @DisplayName("createOrder 메서드는")
    inner class CreateOrder {
        @Test
        fun `주문 및 주문 상세를 생성한다`() {
            val user = createUser()
            val brand = createBrand()
            val product1 = createProduct(brand.id, "상품1", 1000)
            val product2 = createProduct(brand.id, "상품2", 2000)

            val command = OrderCommand.Create(
                userId = user.id,
                totalAmount = 3000,
                brands = listOf(brand),
                products = listOf(product1, product2),
                items = listOf(
                    OrderCommand.OrderDetailCommand(product1.id, 1),
                    OrderCommand.OrderDetailCommand(product2.id, 1),
                ),
            )

            orderService.createOrder(command)

            val orders = orderJpaRepository.findAll()
            val details = orderDetailJpaRepository.findAll()

            assertSoftly { softly ->
                softly.assertThat(orders).hasSize(1)
                softly.assertThat(details).hasSize(2)
                softly.assertThat(details.map { it.productName }).containsExactlyInAnyOrder("상품1", "상품2")
            }
        }
    }

    private fun createUser(
        userId: String = "user123",
        email: String = "test@example.com",
    ): User {
        val command = UserCommand.SignUp(
            userId = userId,
            email = email,
            birthDate = "1990-01-01",
            gender = Gender.MALE,
        )
        return userJpaRepository.save(User.create(command))
    }

    private fun createBrand(name: String = "브랜드_${System.nanoTime()}"): Brand {
        val brand = Brand.create(name)
        return brandJpaRepository.save(brand)
    }

    private fun createProduct(brandId: Long, name: String = "상품_${System.nanoTime()}", price: Long = 1000L): Product {
        val product = Product.create(name, price, brandId)
        return productJpaRepository.save(product)
    }
}
