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
    fun `사용자의 좋아요 목록을 조회할 수 있다`() {
        // given
        val userId = requireNotNull(user.id) { "user.id must not be null" }
        products.forEach { product ->
            likeRepository.save(Like(userId = userId, productId = product.id))
        }
        val pageable = PageRequest.of(0, 20)

        // when
        val result = likeQueryService.getLikesByUserId(userId, pageable)

        // then
        assertThat(result.content).hasSize(3)
        assertThat(result.content.map { it.productId }).containsExactlyInAnyOrder(
            products[0].id,
            products[1].id,
            products[2].id,
        )
        assertThat(result.content.map { it.userId }).allMatch { it == userId }
    }

    @Test
    fun `좋아요가 없으면 빈 목록을 반환한다`() {
        // given
        val userId = requireNotNull(user.id) { "user.id must not be null" }
        val pageable = PageRequest.of(0, 20)

        // when
        val result = likeQueryService.getLikesByUserId(userId, pageable)

        // then
        assertThat(result.content).isEmpty()
    }

    @Test
    fun `페이징 처리가 정상적으로 동작한다`() {
        // given
        val userId = requireNotNull(user.id) { "user.id must not be null" }
        products.forEach { product ->
            likeRepository.save(Like(userId = user.id, productId = product.id))
        }
        val pageable = PageRequest.of(0, 2)

        // when
        val result = likeQueryService.getLikesByUserId(userId, pageable)

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

        val userId = requireNotNull(user.id) { "user.id must not be null" }
        val user2Id = requireNotNull(user2.id) { "user2.id must not be null" }
        val product0Id = requireNotNull(products[0].id) { "products[0].id must not be null" }
        val product1Id = requireNotNull(products[1].id) { "products[1].id must not be null" }
        val product2Id = requireNotNull(products[2].id) { "products[2].id must not be null" }

        // 상품1: 2개 좋아요, 상품2: 1개 좋아요, 상품3: 0개 좋아요
        likeRepository.save(Like(userId = userId, productId = product0Id))
        likeRepository.save(Like(userId = user2Id, productId = product0Id))
        likeRepository.save(Like(userId = userId, productId = product1Id))

        val productIds = listOf(product0Id, product1Id, product2Id)

        // when
        val likeCountMap = likeQueryService.countByProductIdIn(productIds)

        // then
        assertThat(likeCountMap[product0Id]).isEqualTo(2L)
        assertThat(likeCountMap[product1Id]).isEqualTo(1L)
        assertThat(likeCountMap[product2Id]).isNull() // 좋아요가 없으면 map에 포함되지 않음
    }

    @Test
    fun `좋아요가 없는 상품들만 조회하면 빈 맵을 반환한다`() {
        // given
        val productIds = products.map { requireNotNull(it.id) { "product.id must not be null" } }

        // when
        val likeCountMap = likeQueryService.countByProductIdIn(productIds)

        // then
        assertThat(likeCountMap).isEmpty()
    }

    @Test
    fun `빈 리스트로 배치 조회하면 빈 맵을 반환한다`() {
        // given
        val emptyProductIds = emptyList<Long>()

        // when
        val likeCountMap = likeQueryService.countByProductIdIn(emptyProductIds)

        // then
        assertThat(likeCountMap).isEmpty()
    }
}
