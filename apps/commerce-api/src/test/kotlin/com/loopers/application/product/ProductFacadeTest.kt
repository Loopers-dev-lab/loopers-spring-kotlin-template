package com.loopers.application.product

import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.ProductLike
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSort
import com.loopers.domain.user.Gender
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.fixtures.BrandFixtures.createBrand
import com.loopers.support.fixtures.ProductFixtures.createProduct
import com.loopers.support.fixtures.ProductLikeFixtures
import com.loopers.support.fixtures.ProductLikeFixtures.createProductLike
import com.loopers.support.fixtures.UserFixtures.createUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
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
    private val userService: UserService = mockk()
    private val productFacade = ProductFacade(brandService, productService, productLikeService, userService)

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

            val brand = createBrand(brandId, "브랜드A")
            val brands = listOf(brand)

            val product1 = createProduct(1L, "상품1", 10000L, brandId)
            val product2 = createProduct(2L, "상품2", 20000L, brandId)
            val products = listOf(product1, product2)
            val productPage: Page<Product> = PageImpl(products, pageable, products.size.toLong())

            val productLike1 = ProductLikeFixtures.createProductLike(1L, product1.id, userId)
            val productLike2 = ProductLikeFixtures.createProductLike(2L, product2.id, userId)
            val productLikes = listOf(productLike1, productLike2)

            every { productService.getProducts(brandId, sort, pageable) } returns productPage
            every { productLikeService.getAllBy(listOf(1L, 2L)) } returns productLikes
            every { brandService.getAllBrand(listOf(brandId)) } returns brands

            // when
            val result = productFacade.getProducts(brandId, sort, pageable)

            // then
            verify(exactly = 1) { productService.getProducts(brandId, sort, pageable) }
            verify(exactly = 1) { productLikeService.getAllBy(listOf(1L, 2L)) }
            verify(exactly = 1) { brandService.getAllBrand(listOf(brandId)) }

            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(2)
                softly.assertThat(result.totalElements).isEqualTo(2)
            }
        }

        @Test
        fun `상품이 없으면 빈 페이지를 반환한다`() {
            // given
            val brandId = 1L
            val sort = ProductSort.LATEST
            val emptyPage: Page<Product> = PageImpl(emptyList(), pageable, 0L)

            every { productService.getProducts(brandId, sort, pageable) } returns emptyPage

            // when
            val result = productFacade.getProducts(brandId, sort, pageable)

            // then
            verify(exactly = 1) { productService.getProducts(brandId, sort, pageable) }
            verify(exactly = 0) { productLikeService.getAllBy(any<List<Long>>()) }
            verify(exactly = 0) { brandService.getAllBrand(any()) }

            assertSoftly { softly ->
                softly.assertThat(result.content).isEmpty()
                softly.assertThat(result.totalElements).isEqualTo(0)
            }
        }

        @Test
        fun `브랜드 ID가 중복되어도 distinct 처리하여 한 번만 조회한다`() {
            // given
            val brandId = 1L
            val sort = ProductSort.LATEST

            val brand = createBrand(brandId, "브랜드A")
            val brands = listOf(brand)

            val product1 = createProduct(1L, "상품1", 10000L, brandId)
            val product2 = createProduct(2L, "상품2", 20000L, brandId)
            val products = listOf(product1, product2)
            val productPage: Page<Product> = PageImpl(products, pageable, products.size.toLong())

            every { productService.getProducts(null, sort, pageable) } returns productPage
            every { productLikeService.getAllBy(listOf(1L, 2L)) } returns emptyList()
            every { brandService.getAllBrand(listOf(brandId)) } returns brands

            // when
            productFacade.getProducts(null, sort, pageable)

            // then
            verify(exactly = 1) { brandService.getAllBrand(listOf(brandId)) }
        }
    }

    @Nested
    @DisplayName("상품 상세 조회")
    inner class GetProduct {

        @Test
        fun `상품 상세 조회 시 모든 서비스 메서드가 순서대로 호출된다`() {
            // given
            val productId = 1L
            val brandId = 1L
            val userId = "1"

            val product = createProduct(productId, "상품1", 10000L, brandId)
            val brand = createBrand(brandId, "브랜드A")
            val productLike = createProductLike(1L, productId, userId.toLong())
            val productLikes = listOf(productLike)

            every { productService.getProduct(productId) } returns product
            every { brandService.getBrand(brandId) } returns brand
            every { productLikeService.getAllBy(productId) } returns productLikes

            // when
            val result = productFacade.getProduct(productId, userId)

            // then
            verify(exactly = 1) { productService.getProduct(productId) }
            verify(exactly = 1) { brandService.getBrand(brandId) }
            verify(exactly = 1) { productLikeService.getAllBy(productId) }

            assertSoftly { softly ->
                softly.assertThat(result.id).isEqualTo(productId)
                softly.assertThat(result.name).isEqualTo("상품1")
                softly.assertThat(result.brandName).isEqualTo("브랜드A")
                softly.assertThat(result.likeCount).isEqualTo(1L)
                softly.assertThat(result.likedByMe).isTrue()
            }
        }

        @Test
        fun `존재하지 않는 상품은 예외를 발생시킨다`() {
            // given
            val productId = 999L
            val userId = "1"

            every { productService.getProduct(productId) } returns null

            // when & then
            assertThatThrownBy { productFacade.getProduct(productId, userId) }
                .isInstanceOf(CoreException::class.java)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("상품을 찾을 수 없습니다")
        }

        @Test
        fun `userId가 null이면 likedByMe는 false다`() {
            // given
            val productId = 1L
            val brandId = 1L

            val product = createProduct(productId, "상품1", 10000L, brandId)
            val brand = createBrand(brandId, "브랜드A")

            every { productService.getProduct(productId) } returns product
            every { brandService.getBrand(brandId) } returns brand
            every { productLikeService.getAllBy(productId) } returns emptyList()

            // when
            val result = productFacade.getProduct(productId, null)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.likedByMe).isFalse()
                softly.assertThat(result.likeCount).isEqualTo(0L)
            }
        }
    }

    @Nested
    @DisplayName("좋아요한 상품 목록 조회")
    inner class GetLikedProducts {

        @Test
        fun `좋아요한 상품 목록 조회 시 모든 서비스 메서드가 순서대로 호출된다`() {
            // given
            val userId = "1"
            val userIdLong = 1L
            val brandId = 1L

            val user = createUser(userIdLong, "userId", "test@example.com", "1990-01-01", Gender.MALE)

            val product1 = createProduct(1L, "상품1", 10000L, brandId)
            val product2 = createProduct(2L, "상품2", 20000L, brandId)
            val products = listOf(product1, product2)

            val brand = createBrand(brandId, "브랜드A")
            val brands = listOf(brand)

            val productLike1 = createProductLike(1L, product1.id, userIdLong)
            val productLike2 = createProductLike(2L, product2.id, userIdLong)
            val productLikes = listOf(productLike1, productLike2)
            val productLikePage: Page<ProductLike> = PageImpl(productLikes, pageable, productLikes.size.toLong())

            every { userService.getMyInfo(userId) } returns user
            every { productLikeService.getAllBy(userIdLong, pageable) } returns productLikePage
            every { productService.getProducts(listOf(1L, 2L)) } returns products
            every { brandService.getAllBrand(listOf(brandId)) } returns brands

            // when
            val result = productFacade.getLikedProducts(userId, pageable)

            // then
            verify(exactly = 1) { userService.getMyInfo(userId) }
            verify(exactly = 1) { productLikeService.getAllBy(userIdLong, pageable) }
            verify(exactly = 1) { productService.getProducts(listOf(1L, 2L)) }
            verify(exactly = 1) { brandService.getAllBrand(listOf(brandId)) }

            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(2)
                softly.assertThat(result.totalElements).isEqualTo(2)
                softly.assertThat(result.content[0].id).isEqualTo(1L)
                softly.assertThat(result.content[0].name).isEqualTo("상품1")
                softly.assertThat(result.content[0].brandName).isEqualTo("브랜드A")
            }
        }

        @Test
        fun `좋아요한 상품이 없으면 빈 페이지를 반환한다`() {
            // given
            val userId = "1"
            val userIdLong = 1L

            val user = createUser(userIdLong, "userId", "test@example.com", "1990-01-01", Gender.MALE)
            val emptyPage: Page<ProductLike> = PageImpl(emptyList(), pageable, 0L)

            every { userService.getMyInfo(userId) } returns user
            every { productLikeService.getAllBy(userIdLong, pageable) } returns emptyPage

            // when
            val result = productFacade.getLikedProducts(userId, pageable)

            // then
            verify(exactly = 1) { userService.getMyInfo(userId) }
            verify(exactly = 1) { productLikeService.getAllBy(userIdLong, pageable) }
            verify(exactly = 0) { productService.getProducts(any()) }
            verify(exactly = 0) { brandService.getAllBrand(any()) }

            assertSoftly { softly ->
                softly.assertThat(result.content).isEmpty()
                softly.assertThat(result.totalElements).isEqualTo(0)
            }
        }
    }
}
