package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSort
import com.loopers.support.util.withId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

@DisplayName("ProductFacade 단위 테스트")
class ProductFacadeTest {

    private val brandService: BrandService = mockk()
    private val productService: ProductService = mockk()
    private val productLikeService: ProductLikeService = mockk()
    private val productFacade = ProductFacade(brandService, productService, productLikeService)

    private val pageable: Pageable = PageRequest.of(0, 20)

    @Nested
    @DisplayName("상품 목록 조회")
    inner class GetProducts {

        @Test
        fun `상품 목록 조회 시 모든 서비스 메서드가 순서대로 호출된다`() {
            // given
            val brandId = 1L
            val userId = 1L
            val sort = ProductSort.LATEST

            val brand = Brand.create(name = "브랜드A").withId(brandId)
            val brands = listOf(brand)

            val product1 = createProduct(1L, "상품1", 10000L, brandId)
            val product2 = createProduct(2L, "상품2", 20000L, brandId)
            val products = listOf(product1, product2)
            val productPage: Page<Product> = PageImpl(products, pageable, products.size.toLong())

            val productLike1 = createProductLike(1L, product1.id, userId)
            val productLike2 = createProductLike(2L, product2.id, userId)
            val productLikes = listOf(productLike1, productLike2)

            every {
                productService.getProducts(brandId, sort, pageable)
            } returns productPage
            every {
                productLikeService.findAllBy(listOf(1L, 2L))
            } returns productLikes
            every {
                brandService.getAllBrand(listOf(brandId, brandId))
            } returns brands

            // when
            val result = productFacade.getProducts(brandId, sort, pageable)

            // then
            verify(exactly = 1) { productService.getProducts(brandId, sort, pageable) }
            verify(exactly = 1) { productLikeService.findAllBy(listOf(1L, 2L)) }
            verify(exactly = 1) { brandService.getAllBrand(listOf(brandId, brandId)) }

            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(2)
                softly.assertThat(result.totalElements).isEqualTo(2)
            }
        }
    }

    private fun createProduct(
        id: Long,
        name: String,
        price: Long,
        brandId: Long,
    ): Product {
        return Product.create(
            name = name,
            price = price,
            brandId = brandId,
        ).withId(id)
    }

    private fun createProductLike(
        id: Long,
        productId: Long,
        userId: Long,
    ): ProductLike {
        return ProductLike.create(
            productId = productId,
            userId = userId,
        ).withId(id)
    }
}
