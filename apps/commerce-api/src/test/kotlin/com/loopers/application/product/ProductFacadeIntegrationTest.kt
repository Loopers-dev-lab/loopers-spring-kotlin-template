package com.loopers.application.product

import com.loopers.cache.CacheTemplate
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductFacadeIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productRepository: ProductRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val brandRepository: BrandRepository,
    private val cacheTemplate: CacheTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("상품 단건 조회 통합테스트")
    @Nested
    inner class FindProductById {

        @Test
        @DisplayName("상품을 조회하면 결과가 반환된다")
        fun `return product info when product exists`() {
            // given
            val product = createProduct()

            // when
            val result = productFacade.findProductById(product.id)

            // then
            assertThat(result.productId).isEqualTo(product.id)
        }

        @Test
        @DisplayName("상품을 두 번 조회하면 두 번째는 캐시에서 조회된다")
        fun `use cache when product is fetched twice`() {
            // given
            val originalProduct = createProduct(price = Money.krw(10000))

            // given - 첫 번째 조회 (캐시 저장)
            val firstResult = productFacade.findProductById(originalProduct.id)
            val cachedStock = firstResult.stock

            // given - 원본 데이터 수정
            originalProduct.decreaseStock(1)
            productRepository.save(originalProduct)

            // when
            val cachedResult = productFacade.findProductById(originalProduct.id)

            // then - 캐시된 값이 반환됨
            assertThat(cachedResult.stock).isEqualTo(cachedStock)
        }

        @Test
        @DisplayName("실제 Redis에 캐시 데이터가 저장된다")
        fun `cache data is stored in real redis`() {
            // given
            val product = createProduct(name = "테스트 상품", price = Money.krw(50000))
            val cacheKey = ProductCacheKeys.ProductDetail(productId = product.id)

            // when - 상품 조회 (캐시 저장)
            productFacade.findProductById(product.id)

            // then - Redis에서 직접 조회해서 데이터 확인
            val cachedValue = cacheTemplate.get(
                cacheKey,
                object : com.fasterxml.jackson.core.type.TypeReference<com.loopers.domain.product.ProductView>() {},
            )

            assertThat(cachedValue).isNotNull()
            assertThat(cachedValue!!.product.id).isEqualTo(product.id)
            assertThat(cachedValue.product.name).isEqualTo("테스트 상품")
            assertThat(cachedValue.product.price).isEqualTo(Money.krw(50000))
        }
    }

    @DisplayName("상품 목록 조회 통합테스트")
    @Nested
    inner class FindProducts {

        @Test
        @DisplayName("상품 목록을 조회하면 결과가 반환된다")
        fun `return products when products exist`() {
            // given
            createProduct(name = "상품1")
            createProduct(name = "상품2")
            createProduct(name = "상품3")

            val criteria = ProductCriteria.FindProducts(
                page = 0,
                size = 20,
                sort = ProductSortType.LATEST,
                brandId = null,
            )

            // when
            val result = productFacade.findProducts(criteria)

            // then
            assertThat(result.products).hasSizeGreaterThanOrEqualTo(3)
        }

        @Test
        @DisplayName("3페이지 이내 조회는 캐싱된다")
        fun `cache results within 3 pages`() {
            // given
            createProduct(name = "상품1")
            val criteria = ProductCriteria.FindProducts(
                page = 0,
                size = 20,
                sort = ProductSortType.LATEST,
                brandId = null,
            )

            // given - 첫 번째 조회 (캐시 저장)
            val firstResult = productFacade.findProducts(criteria)

            // given - 원본 데이터 추가
            createProduct(name = "새 상품")

            // when
            val cachedResult = productFacade.findProducts(criteria)

            // then - 캐시된 결과 반환 (새 상품 미포함)
            assertThat(cachedResult.products.size).isEqualTo(firstResult.products.size)
        }

        @Test
        @DisplayName("4페이지 이상 조회는 캐싱되지 않는다")
        fun `do not cache results beyond 3 pages`() {
            // given
            repeat(39) { createProduct(name = "상품$it") }

            val criteria = ProductCriteria.FindProducts(
                page = 3,
                size = 10,
                sort = ProductSortType.LATEST,
                brandId = null,
            )

            // given - 첫 번째 조회 (캐시 저장)
            productFacade.findProducts(criteria)

            // given - 원본 데이터 추가
            createProduct(name = "최신 상품")

            // when
            val secondResult = productFacade.findProducts(criteria)

            // then - 기존 9개가 아닌 추가된 10개 체크
            assertThat(secondResult.products).hasSize(10)
        }

        @Test
        @DisplayName("실제 Redis에 상품 목록 캐시가 저장된다")
        fun `product list cache data is stored in real redis`() {
            // given
            createProduct(name = "상품1")
            val criteria = ProductCriteria.FindProducts(
                page = 0,
                size = 20,
                sort = ProductSortType.LATEST,
                brandId = null,
            )
            val cacheKey = ProductCacheKeys.ProductList(
                sort = ProductSortType.LATEST,
                brandId = null,
                page = 0,
                size = 20,
            )

            // when - 상품 목록 조회 (캐시 저장)
            productFacade.findProducts(criteria)

            // then - Redis에서 직접 조회해서 데이터 확인
            val cachedValue = cacheTemplate.get(
                cacheKey,
                object : com.fasterxml.jackson.core.type.TypeReference<CachedProductList>() {},
            )

            assertThat(cachedValue).isNotNull()
            assertThat(cachedValue!!.productIds).hasSizeGreaterThanOrEqualTo(1)
        }

        @Test
        @DisplayName("브랜드별 필터링이 적용된다")
        fun `filter by brand correctly`() {
            // given
            val brand1 = brandRepository.save(Brand.create("브랜드1"))
            val brand2 = brandRepository.save(Brand.create("브랜드2"))

            createProduct(name = "상품1", brandId = brand1.id)
            createProduct(name = "상품2", brandId = brand1.id)
            createProduct(name = "상품3", brandId = brand2.id)

            val criteria = ProductCriteria.FindProducts(
                page = 0,
                size = 20,
                sort = ProductSortType.LATEST,
                brandId = brand1.id,
            )

            // when
            val result = productFacade.findProducts(criteria)

            // then
            assertThat(result.products).hasSize(2)
            assertThat(result.products.map { it.brandId }).containsOnly(brand1.id)
        }
    }

    private fun createProduct(
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stock: Stock = Stock.of(100),
        brandId: Long? = null,
    ): Product {
        val brand = if (brandId != null) {
            brandRepository.findById(brandId)!!
        } else {
            brandRepository.save(Brand.create("테스트 브랜드"))
        }

        val product = Product.create(
            name = name,
            price = price,
            stock = stock,
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }
}
