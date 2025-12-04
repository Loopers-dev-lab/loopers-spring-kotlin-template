package com.loopers.application.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.point.Point
import com.loopers.domain.product.Product
import com.loopers.domain.stock.Stock
import com.loopers.domain.user.User
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import kotlin.jvm.optionals.getOrNull

@SpringBootTest
class OrderFacadeTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val pointJpaRepository: PointJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    ) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문 생성 시")
    @Nested
    inner class CreateOrder {
        @DisplayName("주문이 성공한 경우, 재고와 포인트 차감이 정상 반영되어야 한다.")
        @Test
        fun successWithValidPointAndStock() {
            // arrange
            val currentStock = 50
            val requestQuantity = 5

            val user = userJpaRepository.save(User(username = "testUser", password = "testPassword", email = "test@test.com", birthDate = "2025-10-25", gender = User.Gender.MALE))
            val point = pointJpaRepository.save(Point(user.id, BigDecimal("100.00")))
            val brand = brandJpaRepository.save(Brand(name = "testBrand"))
            val product = productJpaRepository.save(Product(name = "testProduct", price = BigDecimal("1.00"), brandId = brand.id))
            val stock = stockJpaRepository.save(Stock(productId = product.id, quantity = currentStock))

            val orderItems = listOf(
                OrderItemCommand(productId = product.id, quantity = requestQuantity),
            )

            // act & assert
            val order = orderFacade.createOrder(user.id, orderItems)
            assertThat(order.totalAmount).isEqualTo(BigDecimal("5.00"))

            val pointResult = pointJpaRepository.findByUserId(user.id)
            assertAll(
                { assertThat(pointResult).isNotNull() },
                { assertThat(pointResult?.balance).isEqualTo(point.balance.subtract(BigDecimal("5.00"))) },
            )

            val stockResult = stockJpaRepository.findById(stock.id).getOrNull()
            assertAll(
                { assertThat(stockResult).isNotNull() },
                { assertThat(stockResult?.quantity).isEqualTo(stock.quantity - requestQuantity) },
            )
        }

        @DisplayName("존재하지 않는 상품으로 주문 시, NOT_FOUND 에러가 발생한다.")
        @Test
        fun throwsException_whenProductDoesNotExist() {
            // arrange
            val userId = 1L
            val productId = 999L

            val orderItems = listOf(
                OrderItemCommand(productId = productId, quantity = 1),
            )

            // act & assert
            val exception = assertThrows<CoreException> {
                orderFacade.createOrder(userId, orderItems)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("존재하지 않는 상품입니다")
            assertThat(exception.message).contains(productId.toString())
        }

        @DisplayName("재고가 부족한 경우, OUT_OF_STOCK 에러가 발생한다.")
        @Test
        fun throwsException_whenStockIsInsufficient() {
            // arrange
            val currentStock = 50
            val requestQuantity = 100

            val user = userJpaRepository.save(User(username = "testUser", password = "testPassword", email = "test@test.com", birthDate = "2025-10-25", gender = User.Gender.MALE))
            val point = pointJpaRepository.save(Point(user.id, BigDecimal("100.00")))
            val brand = brandJpaRepository.save(Brand(name = "testBrand"))
            val product = productJpaRepository.save(Product(name = "testProduct", price = BigDecimal("1.00"), brandId = brand.id))
            val stock = stockJpaRepository.save(Stock(productId = product.id, quantity = currentStock))

            val orderItems = listOf(
                OrderItemCommand(productId = product.id, quantity = requestQuantity),
            )

            // act & assert
            val exception = assertThrows<CoreException> {
                orderFacade.createOrder(user.id, orderItems)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.OUT_OF_STOCK)
            assertThat(exception.message).contains("재고가 부족합니다")

            val pointResult = pointJpaRepository.findByUserId(user.id)
            assertAll(
                { assertThat(pointResult).isNotNull() },
                { assertThat(pointResult?.balance).isEqualTo(point.balance) },
            )

            val stockResult = stockJpaRepository.findById(stock.id).getOrNull()
            assertAll(
                { assertThat(stockResult).isNotNull() },
                { assertThat(stockResult?.quantity).isEqualTo(stock.quantity) },
            )
        }

        @DisplayName("포인트가 부족한 경우, INSUFFICIENT_POINT 에러가 발생한다.")
        @Test
        fun throwsException_whenPointIsInsufficient() {
            // arrange
            val currentStock = 50
            val requestQuantity = 5

            val user = userJpaRepository.save(User(username = "testUser", password = "testPassword", email = "test@test.com", birthDate = "2025-10-25", gender = User.Gender.MALE))
            val point = pointJpaRepository.save(Point(user.id, BigDecimal("1.00")))
            val brand = brandJpaRepository.save(Brand(name = "testBrand"))
            val product = productJpaRepository.save(Product(name = "testProduct", price = BigDecimal("1.00"), brandId = brand.id))
            val stock = stockJpaRepository.save(Stock(productId = product.id, quantity = currentStock))

            val orderItems = listOf(
                OrderItemCommand(productId = product.id, quantity = requestQuantity),
            )

            // act & assert
            val exception = assertThrows<CoreException> {
                orderFacade.createOrder(user.id, orderItems)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.INSUFFICIENT_POINT)
            assertThat(exception.message).contains("포인트가 부족합니다")

            val pointResult = pointJpaRepository.findByUserId(user.id)
            assertAll(
                { assertThat(pointResult).isNotNull() },
                { assertThat(pointResult?.balance).isEqualTo(point.balance) },
            )

            val stockResult = stockJpaRepository.findById(stock.id).getOrNull()
            assertAll(
                { assertThat(stockResult).isNotNull() },
                { assertThat(stockResult?.quantity).isEqualTo(stock.quantity) },
            )
        }
    }
}
