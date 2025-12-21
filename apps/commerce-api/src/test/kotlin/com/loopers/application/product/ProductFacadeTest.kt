package com.loopers.application.product

import com.loopers.application.dto.PageResult
import com.loopers.application.ranking.RankingFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.ProductLikeService
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductEvent
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSort
import com.loopers.domain.user.Gender
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.fixtures.BrandFixtures.createBrand
import com.loopers.support.fixtures.ProductFixtures.createProduct
import com.loopers.support.fixtures.ProductLikeFixtures.createProductLike
import com.loopers.support.fixtures.ProductLikeFixtures.createProductLikeCount
import com.loopers.support.fixtures.UserFixtures.createUser
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

@DisplayName("ProductFacade 단위 테스트")
class ProductFacadeTest {

    private val brandService: BrandService = mockk()
    private val productService: ProductService = mockk()
    private val productLikeService: ProductLikeService = mockk()
    private val userService: UserService = mockk()
    private val productCache: ProductCache = mockk()
    private val applicationEventPublisher: ApplicationEventPublisher = mockk()
    private val rankingFacade: RankingFacade = mockk()
    private val productFacade =
        ProductFacade(
            brandService,
            productService,
            productLikeService,
            userService,
            productCache,
            applicationEventPublisher,
            rankingFacade,
        )

    private val pageable: Pageable = PageRequest.of(0, 20)

    @Nested
    @DisplayName("상품 목록 조회")
    inner class GetProducts {

        @Test
        fun `캐시에 데이터가 있으면 캐시에서 반환한다`() {
            // given
            val brandId = 1L
            val sort = ProductSort.LATEST

            val product1 = createProduct(1L, "상품1", 10000L, brandId)
            val product2 = createProduct(2L, "상품2", 20000L, brandId)
            val brand = createBrand(brandId, "브랜드A")
            val productLikeCount1 = createProductLikeCount(product1.id, 5L)
            val productLikeCount2 = createProductLikeCount(product2.id, 3L)

            val cachedPageResult = PageResult(
                content = listOf(
                    ProductResult.ListInfo.from(product1, listOf(productLikeCount1), listOf(brand)),
                    ProductResult.ListInfo.from(product2, listOf(productLikeCount2), listOf(brand)),
                ),
                page = 0,
                size = 20,
                totalElements = 2L,
                totalPages = 1,
            )

            every { productCache.getProductList(brandId, sort, pageable) } returns cachedPageResult

            // when
            val result = productFacade.getProducts(brandId, sort, pageable)

            // then
            verify(exactly = 1) { productCache.getProductList(brandId, sort, pageable) }
            verify(exactly = 0) { productService.getProducts(any(), any(), any()) }

            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(2)
                softly.assertThat(result.totalElements).isEqualTo(2)
            }
        }

        @Test
        fun `캐시에 데이터가 없으면 DB에서 조회하고 캐시에 저장한다`() {
            // given
            val brandId = 1L
            val sort = ProductSort.LATEST

            val brand = createBrand(brandId, "브랜드A")
            val brands = listOf(brand)

            val product1 = createProduct(1L, "상품1", 10000L, brandId)
            val product2 = createProduct(2L, "상품2", 20000L, brandId)
            val products = listOf(product1, product2)
            val productPage: Page<Product> = PageImpl(products, pageable, products.size.toLong())

            val productLikeCount1 = createProductLikeCount(product1.id, 5L)
            val productLikeCount2 = createProductLikeCount(product2.id, 3L)
            val productLikeCounts = listOf(productLikeCount1, productLikeCount2)

            every { productCache.getProductList(brandId, sort, pageable) } returns null
            every { productService.getProducts(brandId, sort, pageable) } returns productPage
            every { productLikeService.getCountAllBy(listOf(1L, 2L)) } returns productLikeCounts
            every { brandService.getAllBrand(listOf(brandId)) } returns brands
            justRun { productCache.setProductList(brandId, sort, pageable, any()) }

            // when
            val result = productFacade.getProducts(brandId, sort, pageable)

            // then
            verify(exactly = 1) { productCache.getProductList(brandId, sort, pageable) }
            verify(exactly = 1) { productService.getProducts(brandId, sort, pageable) }
            verify(exactly = 1) { productLikeService.getCountAllBy(listOf(1L, 2L)) }
            verify(exactly = 1) { brandService.getAllBrand(listOf(brandId)) }

            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(2)
                softly.assertThat(result.totalElements).isEqualTo(2)
                softly.assertThat(result.content[0].likeCount).isEqualTo(5L)
                softly.assertThat(result.content[1].likeCount).isEqualTo(3L)
            }
        }

        @Test
        fun `상품이 없으면 빈 페이지를 반환한다`() {
            // given
            val brandId = 1L
            val sort = ProductSort.LATEST
            val emptyPage: Page<Product> = PageImpl(emptyList(), pageable, 0L)

            every { productCache.getProductList(brandId, sort, pageable) } returns null
            every { productService.getProducts(brandId, sort, pageable) } returns emptyPage

            // when
            val result = productFacade.getProducts(brandId, sort, pageable)

            // then
            verify(exactly = 1) { productCache.getProductList(brandId, sort, pageable) }
            verify(exactly = 1) { productService.getProducts(brandId, sort, pageable) }
            verify(exactly = 0) { productLikeService.getCountAllBy(any()) }
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

            val productLikeCount1 = createProductLikeCount(product1.id, 5L)
            val productLikeCount2 = createProductLikeCount(product2.id, 3L)
            val productLikeCounts = listOf(productLikeCount1, productLikeCount2)

            every { productCache.getProductList(null, sort, pageable) } returns null
            every { productService.getProducts(null, sort, pageable) } returns productPage
            every { productLikeService.getCountAllBy(listOf(1L, 2L)) } returns productLikeCounts
            every { brandService.getAllBrand(listOf(brandId)) } returns brands
            justRun { productCache.setProductList(null, sort, pageable, any()) }

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
        fun `캐시에 데이터가 있으면 캐시에서 반환한다`() {
            // given
            val productId = 1L
            val userId = "1"

            val cachedResult = ProductResult.DetailInfo(
                id = productId,
                name = "상품1",
                price = 10000L,
                brandName = "브랜드A",
                likeCount = 10L,
                likedByMe = true,
                rank = null,
                score = null,
            )

            every { productCache.getProductDetail(productId, userId) } returns cachedResult
            every { rankingFacade.getProductRank(productId) } returns null
            justRun { applicationEventPublisher.publishEvent(any<ProductEvent.ProductViewed>()) }

            // when
            val result = productFacade.getProduct(productId, userId)

            // then
            verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ProductEvent.ProductViewed>()) }
            verify(exactly = 1) { productCache.getProductDetail(productId, userId) }
            verify(exactly = 0) { productService.getProduct(any()) }

            assertSoftly { softly ->
                softly.assertThat(result.id).isEqualTo(productId)
                softly.assertThat(result.likedByMe).isTrue()
            }
        }

        @Test
        fun `캐시에 데이터가 없으면 DB에서 조회하고 캐시에 저장한다`() {
            // given
            val productId = 1L
            val brandId = 1L
            val userId = "1"
            val userIdLong = 1L

            val product = createProduct(productId, "상품1", 10000L, brandId)
            val brand = createBrand(brandId, "브랜드A")
            val user = createUser(userIdLong, "testUser", "test@example.com", "1990-01-01", Gender.MALE)
            val productLikeCount = createProductLikeCount(productId, 10L)
            val productLike = createProductLike(1L, productId, userIdLong)

            every { productCache.getProductDetail(productId, userId) } returns null
            every { productService.getProduct(productId) } returns product
            every { brandService.getBrand(brandId) } returns brand
            every { productLikeService.getCountBy(productId) } returns productLikeCount
            every { userService.getMyInfo(userId) } returns user
            every { productLikeService.getBy(productId, userIdLong) } returns productLike
            every { rankingFacade.getProductRank(productId) } returns null
            justRun { applicationEventPublisher.publishEvent(any<ProductEvent.ProductViewed>()) }
            justRun { productCache.setProductDetail(productId, userId, any()) }

            // when
            val result = productFacade.getProduct(productId, userId)

            // then
            verify(exactly = 1) { productCache.getProductDetail(productId, userId) }
            verify(exactly = 1) { productService.getProduct(productId) }
            verify(exactly = 1) { brandService.getBrand(brandId) }
            verify(exactly = 1) { productLikeService.getCountBy(productId) }
            verify(exactly = 1) { applicationEventPublisher.publishEvent(any<ProductEvent.ProductViewed>()) }
            verify(exactly = 2) { userService.getMyInfo(userId) }
            verify(exactly = 1) { productLikeService.getBy(productId, userIdLong) }

            assertSoftly { softly ->
                softly.assertThat(result.id).isEqualTo(productId)
                softly.assertThat(result.name).isEqualTo("상품1")
                softly.assertThat(result.brandName).isEqualTo("브랜드A")
                softly.assertThat(result.likeCount).isEqualTo(10L)
                softly.assertThat(result.likedByMe).isTrue()
            }
        }

        @Test
        fun `존재하지 않는 상품은 예외를 발생시킨다`() {
            // given
            val productId = 999L
            val userId = "1"

            every { productCache.getProductDetail(productId, userId) } returns null
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
            val productLikeCount = createProductLikeCount(productId, 5L)

            every { productCache.getProductDetail(productId, null) } returns null
            every { productService.getProduct(productId) } returns product
            every { brandService.getBrand(brandId) } returns brand
            every { productLikeService.getCountBy(productId) } returns productLikeCount
            every { rankingFacade.getProductRank(productId) } returns null
            justRun { applicationEventPublisher.publishEvent(any<ProductEvent.ProductViewed>()) }
            justRun { productCache.setProductDetail(productId, null, any()) }

            // when
            val result = productFacade.getProduct(productId, null)

            // then
            verify(exactly = 0) { userService.getMyInfo(any()) }
            verify(exactly = 0) { productLikeService.getBy(any(), any()) }

            assertSoftly { softly ->
                softly.assertThat(result.likedByMe).isFalse()
                softly.assertThat(result.likeCount).isEqualTo(5L)
            }
        }

        @Test
        fun `사용자가 좋아요하지 않은 상품은 likedByMe가 false다`() {
            // given
            val productId = 1L
            val brandId = 1L
            val userId = "1"
            val userIdLong = 1L

            val product = createProduct(productId, "상품1", 10000L, brandId)
            val brand = createBrand(brandId, "브랜드A")
            val user = createUser(userIdLong, "testUser", "test@example.com", "1990-01-01", Gender.MALE)
            val productLikeCount = createProductLikeCount(productId, 5L)

            every { productCache.getProductDetail(productId, userId) } returns null
            every { productService.getProduct(productId) } returns product
            every { brandService.getBrand(brandId) } returns brand
            every { productLikeService.getCountBy(productId) } returns productLikeCount
            every { userService.getMyInfo(userId) } returns user
            every { productLikeService.getBy(productId, userIdLong) } returns null
            every { rankingFacade.getProductRank(productId) } returns null
            justRun { applicationEventPublisher.publishEvent(any<ProductEvent.ProductViewed>()) }
            justRun { productCache.setProductDetail(productId, userId, any()) }

            // when
            val result = productFacade.getProduct(productId, userId)

            // then
            assertSoftly { softly ->
                softly.assertThat(result.likedByMe).isFalse()
                softly.assertThat(result.likeCount).isEqualTo(5L)
            }
        }
    }

    @Nested
    @DisplayName("좋아요한 상품 목록 조회")
    inner class GetLikedProducts {

        @Test
        fun `캐시에 데이터가 있으면 캐시에서 반환한다`() {
            // given
            val userId = "1"

            val cachedPageResult = PageResult(
                content = listOf(
                    ProductResult.LikedInfo(
                        id = 1L,
                        name = "상품1",
                        price = 10000L,
                        brandName = "브랜드A",
                        likedCreatedAt = ZonedDateTime.now(),
                    ),
                ),
                page = 0,
                size = 20,
                totalElements = 1L,
                totalPages = 1,
            )

            every { productCache.getLikedProductList(userId, pageable) } returns cachedPageResult

            // when
            val result = productFacade.getLikedProducts(userId, pageable)

            // then
            verify(exactly = 1) { productCache.getLikedProductList(userId, pageable) }
            verify(exactly = 0) { userService.getMyInfo(any()) }

            assertSoftly { softly ->
                softly.assertThat(result.content).hasSize(1)
                softly.assertThat(result.totalElements).isEqualTo(1)
            }
        }

        @Test
        fun `캐시에 데이터가 없으면 DB에서 조회하고 캐시에 저장한다`() {
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
            val productLikePage: Page<com.loopers.domain.like.ProductLike> =
                PageImpl(productLikes, pageable, productLikes.size.toLong())

            every { productCache.getLikedProductList(userId, pageable) } returns null
            every { userService.getMyInfo(userId) } returns user
            every { productLikeService.getAllBy(userIdLong, pageable) } returns productLikePage
            every { productService.getProducts(listOf(1L, 2L)) } returns products
            every { brandService.getAllBrand(listOf(brandId)) } returns brands
            justRun { productCache.setLikedProductList(userId, pageable, any()) }

            // when
            val result = productFacade.getLikedProducts(userId, pageable)

            // then
            verify(exactly = 1) { productCache.getLikedProductList(userId, pageable) }
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
            val emptyPage: Page<com.loopers.domain.like.ProductLike> = PageImpl(emptyList(), pageable, 0L)

            every { productCache.getLikedProductList(userId, pageable) } returns null
            every { userService.getMyInfo(userId) } returns user
            every { productLikeService.getAllBy(userIdLong, pageable) } returns emptyPage

            // when
            val result = productFacade.getLikedProducts(userId, pageable)

            // then
            verify(exactly = 1) { productCache.getLikedProductList(userId, pageable) }
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
