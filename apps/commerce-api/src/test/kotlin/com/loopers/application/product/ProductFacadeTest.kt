package com.loopers.application.product

import com.loopers.domain.like.LikeQueryService
import com.loopers.domain.product.ProductDetailData
import com.loopers.domain.product.ProductQueryService
import com.loopers.domain.product.SortType
import com.loopers.domain.product.Stock
import com.loopers.fixtures.createTestBrand
import com.loopers.fixtures.createTestLike
import com.loopers.fixtures.createTestProduct
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

class ProductFacadeTest {
    private val productQueryService: ProductQueryService = mockk()
    private val likeQueryService: LikeQueryService = mockk()

    private val productFacade = ProductFacade(
        productQueryService,
        likeQueryService,
    )

    @Test
    fun `상품 목록을 조회할 수 있다`() {
        // given
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("100000"), brand = brand)
        product.setLikeCount(10L)
        val pageable = PageRequest.of(0, 20)

        val products = PageImpl(listOf(product))

        every { productQueryService.findProducts(null, SortType.LATEST, pageable) } returns products

        // when
        val result = productFacade.getProducts(null, "latest", pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].id).isEqualTo(100L)
        assertThat(result.content[0].name).isEqualTo("운동화")
        assertThat(result.content[0].likeCount).isEqualTo(10L)
        assertThat(result.content[0].brand.name).isEqualTo("나이키")
    }

    @Test
    fun `상품 상세 정보를 조회할 수 있다`() {
        // given
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("100000"), brand = brand)
        product.setLikeCount(10L)
        val stock = Stock(productId = 100L, quantity = 50)

        val productDetailData = ProductDetailData(product, stock)
        every { productQueryService.getProductDetail(100L) } returns productDetailData

        // when
        val result = productFacade.getProductDetail(100L)

        // then
        assertThat(result.id).isEqualTo(100L)
        assertThat(result.name).isEqualTo("운동화")
        assertThat(result.stockQuantity).isEqualTo(50)
        assertThat(result.likeCount).isEqualTo(10L)
        assertThat(result.brand.name).isEqualTo("나이키")
    }

    @Test
    fun `좋아요한 상품 목록을 조회할 수 있다`() {
        // given
        val userId = 1L
        val brand = createTestBrand(id = 1L, name = "나이키")
        val product = createTestProduct(id = 100L, name = "운동화", price = BigDecimal("100000"), brand = brand)
        val like = createTestLike(id = 1L, userId = userId, productId = product.id)
        val pageable = PageRequest.of(0, 20)

        every { likeQueryService.getValidLikesByUserId(userId, pageable) } returns PageImpl(listOf(like))
        every { productQueryService.getProductsByIds(listOf(100L)) } returns listOf(product)

        // when
        val result = productFacade.getLikedProducts(userId, pageable)

        // then
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].productId).isEqualTo(100L)
        assertThat(result.content[0].productName).isEqualTo("운동화")
        assertThat(result.content[0].brand.name).isEqualTo("나이키")
    }

    @Test
    fun `존재하지 않는 상품 조회 시 예외가 발생한다`() {
        // given
        every { productQueryService.getProductDetail(999L) } throws CoreException(
            com.loopers.support.error.ErrorType.NOT_FOUND,
            "상품을 찾을 수 없습니다: 999",
        )

        // when & then
        assertThatThrownBy {
            productFacade.getProductDetail(999L)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("상품을 찾을 수 없습니다")
    }

    @Test
    fun `재고 정보가 없는 상품 조회 시 예외가 발생한다`() {
        // given
        every { productQueryService.getProductDetail(100L) } throws CoreException(
            com.loopers.support.error.ErrorType.NOT_FOUND,
            "재고 정보를 찾을 수 없습니다: 100",
        )

        // when & then
        assertThatThrownBy {
            productFacade.getProductDetail(100L)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("재고 정보를 찾을 수 없습니다")
    }
}
