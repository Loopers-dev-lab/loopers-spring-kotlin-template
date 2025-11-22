package com.loopers.domain.like

import com.loopers.domain.product.ProductRepository
import com.loopers.fixtures.createTestBrand
import com.loopers.fixtures.createTestProduct
import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
class LikeServiceTest {
    @Autowired
    private lateinit var likeService: LikeService

    @MockkBean
    private lateinit var likeRepository: LikeRepository

    @MockkBean
    private lateinit var productRepository: ProductRepository

    @MockkBean
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Test
    fun `좋아요를 등록할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L
        val brand = createTestBrand(id = 1L, name = "테스트")
        val product = createTestProduct(id = productId, name = "상품", price = BigDecimal("10000"), brand = brand)

        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns false
        every { productRepository.findById(productId) } returns product
        every { likeRepository.save(any()) } returns Like(userId = userId, productId = productId)
        every { redisTemplate.delete(any<String>()) } returns true
        every { redisTemplate.keys(any<String>()) } returns emptySet()

        // when
        likeService.addLike(userId, productId)

        // then
        verify { likeRepository.save(any()) }
        verify { productRepository.findById(productId) }
    }

    @Test
    fun `이미 좋아요한 상품에 다시 좋아요를 시도하면 멱등하게 동작한다`() {
        // given
        val userId = 1L
        val productId = 100L
        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns true

        // when
        likeService.addLike(userId, productId)

        // then
        verify(exactly = 0) { likeRepository.save(any()) }
    }

    @Test
    fun `좋아요를 취소할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L
        val brand = createTestBrand(id = 1L, name = "테스트")
        val product = createTestProduct(id = productId, name = "상품", price = BigDecimal("10000"), brand = brand)

        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns true
        every { productRepository.findById(productId) } returns product
        every { likeRepository.deleteByUserIdAndProductId(userId, productId) } just Runs
        every { redisTemplate.delete(any<String>()) } returns true
        every { redisTemplate.keys(any<String>()) } returns emptySet()

        // when
        likeService.removeLike(userId, productId)

        // then
        verify { likeRepository.deleteByUserIdAndProductId(userId, productId) }
        verify { productRepository.findById(productId) }
    }

    @Test
    fun `좋아요하지 않은 상품에 대해 취소를 시도하면 멱등하게 동작한다`() {
        // given
        val userId = 1L
        val productId = 100L

        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns false

        // when
        likeService.removeLike(userId, productId)

        // then (예외 발생 없이 early return)
        verify(exactly = 0) { likeRepository.deleteByUserIdAndProductId(userId, productId) }
        verify(exactly = 0) { productRepository.findById(any()) }
    }
}
