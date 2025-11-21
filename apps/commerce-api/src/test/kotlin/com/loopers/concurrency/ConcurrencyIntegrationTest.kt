package com.loopers.concurrency

import com.loopers.application.like.LikeFacade
import com.loopers.application.order.CreateOrderRequest
import com.loopers.application.order.OrderFacade
import com.loopers.application.order.OrderItemRequest
import com.loopers.domain.brand.Brand
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.MemberCoupon
import com.loopers.domain.member.BirthDate
import com.loopers.domain.member.Gender
import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberId
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.shared.Email
import com.loopers.domain.shared.Money
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.MemberCouponJpaRepository
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@SpringBootTest
class ConcurrencyIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val orderFacade: OrderFacade,
    private val memberJpaRepository: MemberJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val memberCouponJpaRepository: MemberCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    private lateinit var brand: Brand

    @BeforeEach
    fun setUp() {
        brand = brandJpaRepository.save(Brand(name = "테스트브랜드", description = "설명"))
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일한 상품에 100명이 동시에 좋아요 요청 시 좋아요 개수가 정확히 100개여야 한다")
    @Test
    fun likeConcurrencyTest() {
        val product = createProduct(1000)
        val members = (1..100).map { createMember("user$it", 0) }

        val executor = Executors.newFixedThreadPool(100)
        val latch = CountDownLatch(1)

        members.forEach { member ->
            executor.submit {
                try {
                    latch.await()
                    likeFacade.addLike(member.memberId.value, product.id)
                } catch (e: Exception) {
                    println("좋아요 실패: ${e.message}")
                }
            }
        }

        latch.countDown()
        executor.shutdown()
        while (!executor.isTerminated) Thread.sleep(50)

        val result = productJpaRepository.findById(product.id).get()
        assertThat(result.likesCount).isEqualTo(100)
    }

    @DisplayName("동일한 쿠폰으로 10개 기기에서 동시 주문 시 쿠폰은 1번만 사용되어야 한다")
    @Test
    fun couponConcurrencyTest() {
        val member = createMember("user1", 1_000_000)
        val product = createProduct(100, 10_000)
        val coupon = createCoupon()
        val memberCoupon = issueCoupon(member.memberId.value, coupon)

        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(1)
        var successCount = 0

        repeat(10) {
            executor.submit {
                try {
                    latch.await()
                    orderFacade.createOrder(
                        CreateOrderRequest(
                            memberId = member.memberId.value,
                            items = listOf(OrderItemRequest(product.id, 1)),
                            couponId = coupon.id
                        )
                    )
                    synchronized(this) { successCount++ }
                } catch (e: Exception) {
                    println("쿠폰 주문 실패: ${e.message}")
                }
            }
        }

        latch.countDown()
        executor.shutdown()
        while (!executor.isTerminated) Thread.sleep(50)

        val result = memberCouponJpaRepository.findById(memberCoupon.id).get()
        assertThat(successCount).isEqualTo(1)
        assertThat(result.usedAt).isNotNull()
    }

    @DisplayName("동일한 유저가 여러 주문을 동시에 수행해도 포인트가 정확히 차감되어야 한다")
    @Test
    fun pointConcurrencyTest() {
        val member = createMember("user1", 50_000)
        val products = (1..5).map { createProduct(100, 10_000) }

        val executor = Executors.newFixedThreadPool(5)
        val latch = CountDownLatch(1)

        products.forEach { product ->
            executor.submit {
                try {
                    latch.await()
                    orderFacade.createOrder(
                        CreateOrderRequest(
                            memberId = member.memberId.value,
                            items = listOf(OrderItemRequest(product.id, 1)),
                            couponId = null
                        )
                    )
                } catch (e: Exception) {
                    println("포인트 주문 실패: ${e.message}")
                }
            }
        }

        latch.countDown()
        executor.shutdown()
        while (!executor.isTerminated) Thread.sleep(50)

        val result = memberJpaRepository.findById(member.id).get()
        assertThat(result.point.amount).isEqualTo(0)
    }

    @DisplayName("재고 10개 상품에 20명이 동시 주문 시 10명만 성공해야 한다")
    @Test
    fun stockConcurrencyTest() {
        val product = createProduct(10, 1_000)
        val members = (1..20).map { createMember("user$it", 100_000) }

        val executor = Executors.newFixedThreadPool(20)
        val latch = CountDownLatch(1)
        var successCount = 0

        members.forEach { member ->
            executor.submit {
                try {
                    latch.await()
                    orderFacade.createOrder(
                        CreateOrderRequest(
                            memberId = member.memberId.value,
                            items = listOf(OrderItemRequest(product.id, 1)),
                            couponId = null
                        )
                    )
                    synchronized(this) { successCount++ }
                } catch (e: Exception) {
                    println("재고 주문 실패: ${e.message}")
                }
            }
        }

        latch.countDown()
        executor.shutdown()
        while (!executor.isTerminated) Thread.sleep(50)

        val result = productJpaRepository.findById(product.id).get()
        assertThat(successCount).isEqualTo(10)
        assertThat(result.stock.quantity).isEqualTo(0)
    }

    private fun createMember(id: String, point: Long): Member {
        val member = memberJpaRepository.save(
            Member(
                memberId = MemberId(id),
                email = Email("$id@test.com"),
                birthDate = BirthDate.from("1990-01-01"),
                gender = Gender.MALE
            )
        )
        if (point > 0) {
            member.chargePoint(point)
            memberJpaRepository.save(member)
        }
        return member
    }

    private fun createProduct(stock: Int, price: Long = 10_000): Product {
        return productJpaRepository.save(
            Product(
                name = "상품",
                description = "설명",
                price = Money(price),
                stock = Stock(stock),
                brand = brand
            )
        )
    }

    private fun createCoupon(): Coupon {
        return couponJpaRepository.save(
            Coupon(
                name = "할인쿠폰",
                description = "테스트",
                couponType = CouponType.FIXED_AMOUNT,
                discountAmount = 5000L,
                discountRate = null
            )
        )
    }

    private fun issueCoupon(memberId: String, coupon: Coupon): MemberCoupon {
        return memberCouponJpaRepository.save(MemberCoupon.issue(memberId, coupon))
    }
}


