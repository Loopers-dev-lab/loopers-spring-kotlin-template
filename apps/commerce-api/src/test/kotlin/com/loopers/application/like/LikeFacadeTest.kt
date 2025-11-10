package com.loopers.application.like

import com.loopers.domain.like.LikeQueryService
import com.loopers.domain.like.LikeService
import com.loopers.domain.like.LikedProductData
import com.loopers.fixtures.createTestBrand
import com.loopers.fixtures.createTestLike
import com.loopers.fixtures.createTestProduct
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

class LikeFacadeTest {
    private val likeService: LikeService = mockk(relaxed = true)
    private val likeQueryService: LikeQueryService = mockk()

    private val likeFacade = LikeFacade(
        likeService,
        likeQueryService,
    )

    @Test
    fun `좋아요를 등록할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L

        // when
        likeFacade.addLike(userId, productId)

        // then
        verify { likeService.addLike(userId, productId) }
    }

    @Test
    fun `좋아요를 취소할 수 있다`() {
        // given
        val userId = 1L
        val productId = 100L

        // when
        likeFacade.removeLike(userId, productId)

        // then
        verify { likeService.removeLike(userId, productId) }
    }

    @Test
    fun `좋아요한 상품 목록을 조회할 수 있다`() {
        // given
        val userId = 1L
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("100000"), brand = brand)
        val like = createTestLike(id = 1L, userId = userId, productId = product.id)
        val pageable = PageRequest.of(0, 20)

        val likedProductData = LikedProductData(like, product)
        every { likeQueryService.getLikedProducts(userId, pageable) } returns PageImpl(listOf(likedProductData))

        // when
        val result = likeFacade.getLikedProducts(userId, pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].productId).isEqualTo(100L)
        assertThat(result.content[0].productName).isEqualTo("운동화")
        assertThat(result.content[0].brand.name).isEqualTo("나이키")
    }
}
