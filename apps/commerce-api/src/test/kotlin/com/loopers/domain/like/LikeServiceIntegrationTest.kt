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
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@Transactional
class LikeServiceIntegrationTest {
    @Autowired
    private lateinit var likeService: LikeService

    @Autowired
    private lateinit var likeRepository: LikeRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var brandRepository: BrandRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    private lateinit var user: User
    private lateinit var product: Product

    @BeforeEach
    fun setUp() {
        // 사용자 생성
        user = User(
            name = "홍길동",
            email = "like-test@example.com",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 1)
        )
        user = userRepository.save(user)

        // 브랜드 생성
        val brand = Brand(name = "좋아요테스트브랜드", description = "테스트용 브랜드")
        val savedBrand = brandRepository.save(brand)

        // 상품 생성
        product = Product(
            name = "좋아요테스트상품",
            price = Price(BigDecimal("100000"), Currency.KRW),
            brand = savedBrand
        )
        product = productRepository.save(product)
    }

    @Test
    fun `좋아요를 등록할 수 있다`() {
        // when
        likeService.addLike(user.id, product.id)

        // then
        val exists = likeRepository.existsByUserIdAndProductId(user.id, product.id)
        assertThat(exists).isTrue()

        val likeCount = likeRepository.countByProductId(product.id)
        assertThat(likeCount).isEqualTo(1L)
    }

    @Test
    fun `이미 좋아요한 상품에 다시 좋아요를 시도하면 멱등하게 동작한다`() {
        // given
        likeService.addLike(user.id, product.id)
        val initialCount = likeRepository.countByProductId(product.id)

        // when
        likeService.addLike(user.id, product.id)

        // then
        val finalCount = likeRepository.countByProductId(product.id)
        assertThat(finalCount).isEqualTo(initialCount).isEqualTo(1L)
    }

    @Test
    fun `좋아요를 취소할 수 있다`() {
        // given
        likeService.addLike(user.id, product.id)
        assertThat(likeRepository.existsByUserIdAndProductId(user.id, product.id)).isTrue()

        // when
        likeService.removeLike(user.id, product.id)

        // then
        val exists = likeRepository.existsByUserIdAndProductId(user.id, product.id)
        assertThat(exists).isFalse()

        val likeCount = likeRepository.countByProductId(product.id)
        assertThat(likeCount).isEqualTo(0L)
    }

    @Test
    fun `좋아요하지 않은 상품에 대해 취소를 시도해도 예외가 발생하지 않는다`() {
        // given
        assertThat(likeRepository.existsByUserIdAndProductId(user.id, product.id)).isFalse()

        // when
        likeService.removeLike(user.id, product.id)

        // then (예외 없이 성공)
        val exists = likeRepository.existsByUserIdAndProductId(user.id, product.id)
        assertThat(exists).isFalse()
    }

    @Test
    fun `존재하지 않는 상품에 좋아요를 시도하면 예외가 발생한다`() {
        // when & then
        assertThatThrownBy {
            likeService.addLike(user.id, 99999L)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품을 찾을 수 없습니다")
    }

    @Test
    fun `여러 사용자가 같은 상품에 좋아요할 수 있다`() {
        // given
        val user2 = User(
            name = "김철수",
            email = "like-test2@example.com",
            gender = Gender.MALE,
            birthDate = LocalDate.of(1995, 5, 5)
        )
        val savedUser2 = userRepository.save(user2)

        // when
        likeService.addLike(user.id, product.id)
        likeService.addLike(savedUser2.id, product.id)

        // then
        val likeCount = likeRepository.countByProductId(product.id)
        assertThat(likeCount).isEqualTo(2L)

        assertThat(likeRepository.existsByUserIdAndProductId(user.id, product.id)).isTrue()
        assertThat(likeRepository.existsByUserIdAndProductId(savedUser2.id, product.id)).isTrue()
    }
}
