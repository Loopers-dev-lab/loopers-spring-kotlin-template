package com.loopers.interfaces.event.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountAmount
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.UsageStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.PaymentFailedEventV1
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
import java.time.ZonedDateTime

/**
 * CouponEventListener E2E 통합 테스트
 *
 * 검증 범위:
 * - PaymentFailedEventV1 → 쿠폰 복구 (BEFORE_COMMIT, 동기)
 */
@SpringBootTest
@DisplayName("CouponEventListener 통합 테스트")
class CouponEventListenerIntegrationTest @Autowired constructor(
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val couponRepository: CouponRepository,
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
        @DisplayName("PaymentFailedEventV1 발행 시 사용된 쿠폰이 복구된다")
        fun `PaymentFailedEventV1 with issuedCouponId restores coupon`() {
            // given
            val user = createUser()
            val coupon = createCoupon()
            val issuedCoupon = createIssuedCouponWithUsedStatus(user = user, coupon = coupon)
            val order = createOrderWithItems(userId = user.id)

            val event = PaymentFailedEventV1(
                paymentId = 1L,
                orderId = order.id,
                userId = user.id,
                usedPoint = Money.ZERO_KRW,
                issuedCouponId = issuedCoupon.id,
            )

            // when - BEFORE_COMMIT 이벤트이므로 트랜잭션 내에서 발행해야 함
            transactionTemplate.execute {
                applicationEventPublisher.publishEvent(event)
            }

            // then - 트랜잭션 커밋 후 쿠폰 상태 확인
            val restoredCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
            assertThat(restoredCoupon.status).isEqualTo(UsageStatus.AVAILABLE)
        }

        @Test
        @DisplayName("PaymentFailedEventV1 발행 시 issuedCouponId가 null이면 변경 없음")
        fun `PaymentFailedEventV1 without issuedCouponId does nothing`() {
            // given
            val user = createUser()
            val coupon = createCoupon()
            val issuedCoupon = createIssuedCouponWithUsedStatus(user = user, coupon = coupon)
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

            // then - 트랜잭션 커밋 후 쿠폰 상태 확인 (변경 없음)
            val unchangedCoupon = issuedCouponRepository.findById(issuedCoupon.id)!!
            assertThat(unchangedCoupon.status).isEqualTo(UsageStatus.USED)
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

    private fun createCoupon(): Coupon {
        return couponRepository.save(
            Coupon.of(
                name = "테스트 쿠폰",
                discountAmount = DiscountAmount(
                    type = DiscountType.FIXED_AMOUNT,
                    value = 1000L,
                ),
            ),
        )
    }

    private fun createIssuedCouponWithUsedStatus(user: User, coupon: Coupon): IssuedCoupon {
        val issuedCoupon = coupon.issue(user.id)
        issuedCoupon.use(user.id, coupon, ZonedDateTime.now())
        return issuedCouponRepository.save(issuedCoupon)
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
