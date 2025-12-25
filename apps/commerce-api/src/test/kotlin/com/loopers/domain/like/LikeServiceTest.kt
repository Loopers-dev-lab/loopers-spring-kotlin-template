package com.loopers.domain.like

import com.loopers.domain.product.ProductCacheRepository
import com.loopers.domain.product.ProductLikeCountService
import com.loopers.domain.product.ProductRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class LikeServiceTest @Autowired constructor(
    private val likeService: LikeService,
) {
    @MockkBean
    private lateinit var likeRepository: LikeRepository

    @MockkBean
    private lateinit var productRepository: ProductRepository

    @MockkBean
    private lateinit var productLikeCountService: ProductLikeCountService

    @MockkBean
    private lateinit var productCacheRepository: ProductCacheRepository

    @Test
    fun `좋아요를 등록할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L

        every { likeRepository.existsByUserIdAndProductId(userId, productId) } returns false
        every { productRepository.existsById(productId) } returns true
        every { likeRepository.save(any()) } returns Like(userId = userId, productId = productId)
        every { productLikeCountService.increment(productId) } returns 1L
        every { productCacheRepository.buildProductDetailCacheKey(productId) } returns "product:detail:$productId"
        justRun { productCacheRepository.delete(any()) }
        every { productCacheRepository.getProductListCachePattern() } returns "product:list:*"
        justRun { productCacheRepository.deleteByPattern(any()) }

        // when
        likeService.addLike(userId, productId)

        // then
        verify { likeRepository.save(any()) }
        verify { productRepository.existsById(productId) }
        verify { productLikeCountService.increment(productId) }
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

        every { productRepository.existsById(productId) } returns true
        every { likeRepository.deleteByUserIdAndProductId(userId, productId) } returns 1
        every { productLikeCountService.decrement(productId) } returns 0L
        every { productCacheRepository.buildProductDetailCacheKey(productId) } returns "product:detail:$productId"
        justRun { productCacheRepository.delete(any()) }
        every { productCacheRepository.getProductListCachePattern() } returns "product:list:*"
        justRun { productCacheRepository.deleteByPattern(any()) }

        // when
        likeService.removeLike(userId, productId)

        // then
        verify { likeRepository.deleteByUserIdAndProductId(userId, productId) }
        verify { productRepository.existsById(productId) }
        verify { productLikeCountService.decrement(productId) }
    }

    @Test
    fun `좋아요하지 않은 상품에 대해 취소를 시도하면 멱등하게 동작한다`() {
        // given
        val userId = 1L
        val productId = 100L

        every { productRepository.existsById(productId) } returns true
        every { likeRepository.deleteByUserIdAndProductId(userId, productId) } returns 0

        // when
        likeService.removeLike(userId, productId)

        // then (예외 발생 없이 early return, 실제 삭제가 없으면 Redis 감소도 호출되지 않음)
        verify { likeRepository.deleteByUserIdAndProductId(userId, productId) }
        verify { productRepository.existsById(productId) }
        verify(exactly = 0) { productLikeCountService.decrement(any()) }
    }
}
