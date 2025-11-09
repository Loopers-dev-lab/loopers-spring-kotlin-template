package com.loopers.domain.like

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Transactional
class LikeQueryServiceIntegrationTest {
    @Autowired
    private lateinit var likeQueryService: LikeQueryService

    @Autowired
    private lateinit var likeRepository: LikeRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var brandRepository: BrandRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    private lateinit var user: User
    private lateinit var brand: Brand
    private lateinit var products: List<Product>

    @BeforeEach
    fun setUp() {
        // 사용자 생성
        user = User(
            name = "홍길동",
            email = "like-query-test@example.com",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 1),
        )
        user = userRepository.save(user)

        // 브랜드 생성
        brand = Brand(name = "테스트브랜드", description = "테스트용 브랜드")
        brand = brandRepository.save(brand)

        // 상품 3개 생성
        products = listOf(
            Product(
                name = "상품1",
                price = Price(BigDecimal("10000"), Currency.KRW),
                brand = brand,
            ),
            Product(
                name = "상품2",
                price = Price(BigDecimal("20000"), Currency.KRW),
                brand = brand,
            ),
            Product(
                name = "상품3",
                price = Price(BigDecimal("30000"), Currency.KRW),
                brand = brand,
            ),
        ).map { productRepository.save(it) }
    }

    @Test
    fun `좋아요한 상품 목록을 조회할 수 있다`() {
        // given
        products.forEach { product ->
            likeRepository.save(Like(userId = user.id, productId = product.id))
        }
        val pageable = PageRequest.of(0, 20)

        // when
        val result = likeQueryService.getLikedProducts(user.id, pageable)

        // then
        assertThat(result.content).hasSize(3)
        assertThat(result.content.map { it.product.name }).containsExactlyInAnyOrder("상품1", "상품2", "상품3")
        assertThat(result.content.map { it.like.userId }).allMatch { it == user.id }
    }

    @Test
    fun `좋아요한 상품이 없으면 빈 목록을 반환한다`() {
        // given
        val pageable = PageRequest.of(0, 20)

        // when
        val result = likeQueryService.getLikedProducts(user.id, pageable)

        // then
        assertThat(result.content).isEmpty()
    }

    @Test
    fun `페이징 처리가 정상적으로 동작한다`() {
        // given
        products.forEach { product ->
            likeRepository.save(Like(userId = user.id, productId = product.id))
        }
        val pageable = PageRequest.of(0, 2)

        // when
        val result = likeQueryService.getLikedProducts(user.id, pageable)

        // then
        assertThat(result.content).hasSize(2)
        assertThat(result.totalElements).isEqualTo(3)
        assertThat(result.totalPages).isEqualTo(2)
    }

    @Test
    fun `여러 상품의 좋아요 수를 배치로 조회할 수 있다`() {
        // given
        val user2 = User(
            name = "김철수",
            email = "batch-test@example.com",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1995, 5, 5),
        )
        userRepository.save(user2)

        // 상품1: 2개 좋아요, 상품2: 1개 좋아요, 상품3: 0개 좋아요
        likeRepository.save(Like(userId = user.id, productId = products[0].id))
        likeRepository.save(Like(userId = user2.id, productId = products[0].id))
        likeRepository.save(Like(userId = user.id, productId = products[1].id))

        val productIds = products.map { it.id }

        // when
        val likeCountMap = likeRepository.countByProductIdIn(productIds)

        // then
        assertThat(likeCountMap[products[0].id]).isEqualTo(2L)
        assertThat(likeCountMap[products[1].id]).isEqualTo(1L)
        assertThat(likeCountMap[products[2].id]).isNull() // 좋아요가 없으면 map에 포함되지 않음
    }

    @Test
    fun `좋아요가 없는 상품들만 조회하면 빈 맵을 반환한다`() {
        // given
        val productIds = products.map { it.id }

        // when
        val likeCountMap = likeRepository.countByProductIdIn(productIds)

        // then
        assertThat(likeCountMap).isEmpty()
    }

    @Test
    fun `빈 리스트로 배치 조회하면 빈 맵을 반환한다`() {
        // given
        val emptyProductIds = emptyList<Long>()

        // when
        val likeCountMap = likeRepository.countByProductIdIn(emptyProductIds)

        // then
        assertThat(likeCountMap).isEmpty()
    }
}
