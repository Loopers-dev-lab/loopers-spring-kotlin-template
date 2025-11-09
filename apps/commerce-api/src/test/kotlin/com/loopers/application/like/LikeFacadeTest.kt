package com.loopers.application.like

import com.loopers.domain.brand.Brand
import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeQueryService
import com.loopers.domain.like.LikeService
import com.loopers.domain.like.LikedProductData
import com.loopers.domain.product.Currency
import com.loopers.domain.product.Price
import com.loopers.domain.product.Product
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

    private fun createTestProduct(id: Long, name: String, price: BigDecimal, brand: Brand): Product {
        return Product(
            name = name,
            price = Price(price, Currency.KRW),
            brand = brand,
        ).apply {
            val idField = Product::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }
    }

    private fun createTestBrand(id: Long, name: String): Brand {
        return Brand(name = name, description = "Test Description").apply {
            val idField = Brand::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }
    }

    private fun createTestLike(userId: Long, productId: Long): Like {
        return Like(userId = userId, productId = productId).apply {
            val idField = Like::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, 1L)
        }
    }

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
        val brand = createTestBrand(1L, "나이키")
        val product = createTestProduct(100L, "운동화", BigDecimal("100000"), brand)
        val like = createTestLike(userId, product.id)
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
