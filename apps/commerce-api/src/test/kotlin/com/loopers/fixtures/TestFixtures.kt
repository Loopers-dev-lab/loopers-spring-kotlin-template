package com.loopers.fixtures

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.point.Point
import com.loopers.domain.point.PointRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.infrastructure.like.LikeJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.product.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

/**
 * 통합 테스트에서 엔티티를 생성하고 데이터를 초기화하는 기능을 제공하는 클래스
 */
@Component
class TestFixtures(
    private val userRepository: UserRepository,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val pointRepository: PointRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val userJpaRepository: UserJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val pointJpaRepository: PointJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val likeJpaRepository: LikeJpaRepository,
) {
    private val userEmailCounter = AtomicLong(0)

    /**
     * 모든 테스트 데이터를 삭제합니다.
     */
    fun clear() {
        orderJpaRepository.deleteAll()
        likeJpaRepository.deleteAll()
        userCouponJpaRepository.deleteAll()
        couponJpaRepository.deleteAll()
        pointJpaRepository.deleteAll()
        stockJpaRepository.deleteAll()
        productJpaRepository.deleteAll()
        brandJpaRepository.deleteAll()
        userJpaRepository.deleteAll()
        userEmailCounter.set(0)
    }

    /**
     * 테스트용 사용자를 저장합니다.
     */
    fun saveUser(
        name: String = "테스트 유저",
        email: String? = null,
        gender: Gender = Gender.MALE,
        birthDate: LocalDate = LocalDate.of(1990, 1, 1),
    ): User {
        val userEmail = email ?: "test${userEmailCounter.incrementAndGet()}@example.com"
        val user = User(
            name = name,
            email = userEmail,
            gender = gender,
            birthDate = birthDate,
        )
        return userRepository.save(user)
    }

    /**
     * 테스트용 브랜드를 저장합니다.
     */
    fun saveBrand(brand: Brand): Brand = brandRepository.save(brand)

    /**
     * 테스트용 상품을 저장합니다.
     */
    fun saveProduct(product: Product): Product = productRepository.save(product)

    /**
     * 테스트용 재고를 저장합니다.
     */
    fun saveStock(stock: Stock): Stock = stockRepository.save(stock)

    /**
     * 테스트용 포인트를 저장합니다.
     */
    fun savePoint(point: Point): Point = pointRepository.save(point)

    /**
     * 테스트용 쿠폰을 저장합니다.
     */
    fun saveCoupon(coupon: Coupon): Coupon = couponRepository.save(coupon)

    /**
     * 테스트용 사용자 쿠폰을 저장합니다.
     */
    fun saveUserCoupon(userCoupon: UserCoupon): UserCoupon = userCouponRepository.save(userCoupon)
}

/**
 * 테스트용 엔티티를 생성할 때 BaseEntity의 필드를 설정하는 헬퍼 함수
 *
 * 이 함수는 리플렉션을 사용하지만, 모든 리플렉션 로직이 한 곳에 집중되어 있어
 * 유지보수가 용이하고 테스트 코드의 가독성을 향상시킵니다.
 *
 * @param id 설정할 ID 값
 * @param createdAt 생성 시점 (기본값: 현재 시간)
 * @param updatedAt 수정 시점 (기본값: 현재 시간)
 * @return 필드가 설정된 엔티티
 */
fun <T : BaseEntity> T.withId(
    id: Long,
    createdAt: ZonedDateTime = ZonedDateTime.now(),
    updatedAt: ZonedDateTime = ZonedDateTime.now(),
): T {
    val superclass = this::class.java.superclass

    val idField = superclass.getDeclaredField("id")
    idField.isAccessible = true
    idField.set(this, id)

    val createdAtField = superclass.getDeclaredField("createdAt")
    createdAtField.isAccessible = true
    createdAtField.set(this, createdAt)

    val updatedAtField = superclass.getDeclaredField("updatedAt")
    updatedAtField.isAccessible = true
    updatedAtField.set(this, updatedAt)

    return this
}
