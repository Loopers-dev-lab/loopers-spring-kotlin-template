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
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.jvm.optionals.getOrNull

@SpringBootTest
class OrderFacadeConcurrencyTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val pointJpaRepository: PointJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    ) {
    @AfterEach
    fun cleanUp() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 유저가 서로 다른 주문을 동시에 수행해도, 포인트가 정상적으로 차감되어야 한다.")
    @Test
    fun multipleOrderBySameUser() {
        // arrange
        val currentPoint = BigDecimal("100.00")
        val orderCount = 10

        // act
        val user = userJpaRepository.save(User(username = "testUser", password = "testPassword", email = "test@test.com", birthDate = "2025-10-25", gender = User.Gender.MALE))
        val point = pointJpaRepository.save(Point(user.id, currentPoint))
        val brand = brandJpaRepository.save(Brand(name = "testBrand"))
        val product = productJpaRepository.save(Product(name = "testProduct", price = BigDecimal("1.00"), brandId = brand.id))
        stockJpaRepository.save(Stock(productId = product.id, quantity = 50))

        val latch = CountDownLatch(orderCount)
        val executor = Executors.newFixedThreadPool(orderCount)

        repeat(orderCount) {
            executor.submit {
                try {
                    orderFacade.createOrder(
                        userId = user.id,
                        items = listOf(
                            OrderItemCommand(productId = product.id, quantity = 1),
                        ),
                    )
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        // assert
        val pointResult = pointJpaRepository.findByUserId(user.id)
        assertAll(
            { assertThat(pointResult).isNotNull() },
            { assertThat(pointResult?.balance).isEqualTo(point.balance.subtract(BigDecimal("10.00"))) },
        )
    }

    @DisplayName("동일한 상품에 대해 여러 주문이 동시에 요청되어도, 재고가 정상적으로 차감되어야 한다.")
    @Test
    fun multipleOrderOnSameProduct() {
        // arrange
        val currentQuantity = 500
        val userCount = 100

        // act
        val brand = brandJpaRepository.save(Brand(name = "testBrand"))
        val product = productJpaRepository.save(Product(name = "testProduct", price = BigDecimal("1.00"), brandId = brand.id))
        val stock = stockJpaRepository.save(Stock(productId = product.id, quantity = currentQuantity))

        val userList = ArrayList<User>()
        for (i: Int in 1..userCount) {
            val user = userJpaRepository.save(
                User(
                    username = "test$i",
                    password = "testPassword",
                    email = "test@test.com",
                    birthDate = "2025-10-25",
                    gender = User.Gender.MALE,
                ),
            )
            userList.add(user)

            pointJpaRepository.save(Point(user.id, BigDecimal("100.00")))
        }

        val latch = CountDownLatch(userCount)
        val executor = Executors.newFixedThreadPool(userCount)

        for (user in userList) {
            executor.submit {
                try {
                    orderFacade.createOrder(
                        userId = user.id,
                        items = listOf(
                            OrderItemCommand(productId = product.id, quantity = 5),
                        ),
                    )
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()

        // assert
        val stockResult = stockJpaRepository.findById(stock.id).getOrNull()
        assertAll(
            { assertThat(stockResult).isNotNull() },
            { assertThat(stockResult?.quantity).isEqualTo(currentQuantity - (5 * userCount)) },
        )
    }
}
