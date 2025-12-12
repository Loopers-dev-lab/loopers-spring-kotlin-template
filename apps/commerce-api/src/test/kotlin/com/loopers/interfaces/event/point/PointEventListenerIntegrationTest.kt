package com.loopers.interfaces.event.point

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.PaymentFailedEventV1
import com.loopers.domain.point.PointAccount
import com.loopers.domain.point.PointAccountRepository
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

/**
 * PointEventListener E2E 통합 테스트
 *
 * 검증 범위:
 * - PaymentFailedEventV1 → 포인트 복구 (BEFORE_COMMIT, 동기)
 */
@SpringBootTest
@DisplayName("PointEventListener 통합 테스트")
class PointEventListenerIntegrationTest @Autowired constructor(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val pointAccountRepository: PointAccountRepository,
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Nested
    @DisplayName("onPaymentFailed")
    inner class OnPaymentFailed {

        @Test
        @DisplayName("PaymentFailedEventV1 발행 시 사용된 포인트가 복구된다")
        fun `PaymentFailedEventV1 with usedPoint greater than zero restores point`() {
            // given
            val initialBalance = Money.krw(5000)
            val usedPoint = Money.krw(3000)
            val user = createUser()
            createPointAccount(userId = user.id, balance = initialBalance)
            val order = createOrderWithItems(userId = user.id)

            val event = PaymentFailedEventV1(
                paymentId = 1L,
                orderId = order.id,
                userId = user.id,
                usedPoint = usedPoint,
                issuedCouponId = null,
            )

            // when - BEFORE_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 트랜잭션 커밋 후 포인트 확인
            val pointAccount = pointAccountRepository.findByUserId(user.id)!!
            assertThat(pointAccount.balance).isEqualTo(initialBalance.plus(usedPoint))
        }

        @Test
        @DisplayName("PaymentFailedEventV1 발행 시 사용된 포인트가 0이면 변경 없음")
        fun `PaymentFailedEventV1 with usedPoint zero does nothing`() {
            // given
            val initialBalance = Money.krw(5000)
            val user = createUser()
            createPointAccount(userId = user.id, balance = initialBalance)
            val order = createOrderWithItems(userId = user.id)

            val event = PaymentFailedEventV1(
                paymentId = 1L,
                orderId = order.id,
                userId = user.id,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = null,
            )

            // when - BEFORE_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 트랜잭션 커밋 후 포인트 확인 (변경 없음)
            val pointAccount = pointAccountRepository.findByUserId(user.id)!!
            assertThat(pointAccount.balance).isEqualTo(initialBalance)
        }
    }

    // ===========================================
    // 도메인 픽스처 헬퍼
    // ===========================================

    private var userCounter = 0

    private fun createUser(): User {
        userCounter++
        return userRepository.save(
            User.of(
                username = "user$userCounter",
                birth = LocalDate.of(1990, 1, 1),
                email = "user$userCounter@test.com",
                gender = Gender.MALE,
            ),
        )
    }

    private fun createPointAccount(userId: Long, balance: Money): PointAccount {
        return pointAccountRepository.save(PointAccount.of(userId, balance))
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
