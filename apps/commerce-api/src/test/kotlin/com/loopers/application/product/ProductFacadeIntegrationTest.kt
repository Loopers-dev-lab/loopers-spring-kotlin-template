package com.loopers.application.product

import com.loopers.cache.CacheTemplate
import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.ProductViewedEventV1
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.infrastructure.ranking.RankingKeyGenerator
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
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents

@SpringBootTest
@RecordApplicationEvents
class ProductFacadeIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val brandRepository: BrandRepository,
    private val cacheTemplate: CacheTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val redisTemplate: RedisTemplate<String, String>,
    private val rankingKeyGenerator: RankingKeyGenerator,
) {
    @Autowired
    private lateinit var applicationEvents: ApplicationEvents

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
            assertThat(result.rank).isNull() // 랭킹이 없으면 null
        }

        @Test
        @DisplayName("랭킹 데이터가 있으면 rank 필드에 순위가 반환된다")
        fun `return rank when product has ranking data`() {
            // given
            val product1 = createProduct(name = "1등 상품")
            val product2 = createProduct(name = "2등 상품")
            val product3 = createProduct(name = "3등 상품")

            val bucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)
            redisTemplate.opsForZSet().add(bucketKey, product1.id.toString(), 100.0)
            redisTemplate.opsForZSet().add(bucketKey, product2.id.toString(), 80.0)
            redisTemplate.opsForZSet().add(bucketKey, product3.id.toString(), 60.0)

            // when
            val result1 = productFacade.findProductById(product1.id)
            val result2 = productFacade.findProductById(product2.id)
            val result3 = productFacade.findProductById(product3.id)

            // then
            assertThat(result1.rank).isEqualTo(1)
            assertThat(result2.rank).isEqualTo(2)
            assertThat(result3.rank).isEqualTo(3)
        }

        @Test
        @DisplayName("상품을 두 번 조회하면 두 번째는 캐시에서 조회된다")
        fun `use cache when product is fetched twice`() {
            // given
            val originalProduct = createProduct(price = Money.krw(10000), stockQuantity = 100)

            // given - 첫 번째 조회 (캐시 저장)
            val firstResult = productFacade.findProductById(originalProduct.id)
            val cachedStock = firstResult.stock

            // given - 원본 데이터 수정
            val stock = stockRepository.findByProductId(originalProduct.id)!!
            stock.decrease(1)
            stockRepository.save(stock)

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
                object : com.fasterxml.jackson.core.type.TypeReference<CachedProductDetailV1>() {},
            )

            assertThat(cachedValue).isNotNull()
            assertThat(cachedValue!!.productId).isEqualTo(product.id)
            assertThat(cachedValue.productName).isEqualTo("테스트 상품")
            assertThat(cachedValue.price).isEqualTo(50000L)
        }

        @Test
        @DisplayName("상품을 조회하면 ProductViewedEventV1이 발행된다")
        fun `findProductById() publishes ProductViewedEventV1`() {
            // given
            val product = createProduct()

            // when
            productFacade.findProductById(product.id)

            // then
            val events = applicationEvents.stream(ProductViewedEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.productId).isEqualTo(product.id)
            assertThat(event.userId).isNull()
        }

        @Test
        @DisplayName("userId와 함께 상품을 조회하면 userId가 포함된 이벤트가 발행된다")
        fun `findProductById() with userId publishes event with userId`() {
            // given
            val product = createProduct()
            val userId = 123L

            // when
            productFacade.findProductById(product.id, userId)

            // then
            val events = applicationEvents.stream(ProductViewedEventV1::class.java).toList()
            assertThat(events).hasSize(1)

            val event = events[0]
            assertThat(event.productId).isEqualTo(product.id)
            assertThat(event.userId).isEqualTo(userId)
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
            val cacheKey = ProductCacheKeys.ProductList.from(
                com.loopers.domain.product.PageQuery.of(
                    page = 0,
                    size = 20,
                    sort = ProductSortType.LATEST,
                    brandId = null,
                ),
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

        @Test
        @DisplayName("목록 캐시 히트 시 detail 캐시를 먼저 조회하고 없는 것만 DB에서 가져온다")
        fun `use detail cache and fetch missing from db when list cache hits`() {
            // given - 상품 3개 생성
            val product1 = createProduct(name = "상품1", price = Money.krw(1000), stockQuantity = 100)
            val product2 = createProduct(name = "상품2", price = Money.krw(2000), stockQuantity = 100)
            val product3 = createProduct(name = "상품3", price = Money.krw(3000), stockQuantity = 100)

            val criteria = ProductCriteria.FindProducts(
                page = 0,
                size = 20,
                sort = ProductSortType.LATEST,
                brandId = null,
            )

            // given - 첫 번째 조회 (목록 캐시 + detail 캐시 3개 저장)
            val firstResult = productFacade.findProducts(criteria)
            assertThat(firstResult.products).hasSizeGreaterThanOrEqualTo(3)

            // given - product2의 detail 캐시만 삭제
            val product2CacheKey = ProductCacheKeys.ProductDetail(productId = product2.id)
            cacheTemplate.evict(product2CacheKey)

            // given - product2의 재고 변경
            val originalStock = stockRepository.findByProductId(product2.id)!!
            val originalQuantity = originalStock.quantity
            originalStock.decrease(10)
            stockRepository.save(originalStock)

            // when - 두 번째 조회 (목록 캐시 히트, detail 캐시 부분 히트)
            val secondResult = productFacade.findProducts(criteria)

            // then - product1, product3는 캐시에서 가져오고 product2는 DB에서 가져옴
            val resultProduct1 = secondResult.products.find { it.productId == product1.id }
            val resultProduct2 = secondResult.products.find { it.productId == product2.id }
            val resultProduct3 = secondResult.products.find { it.productId == product3.id }

            assertThat(resultProduct1).isNotNull()
            assertThat(resultProduct1!!.price).isEqualTo(Money.krw(1000)) // 캐시된 값

            assertThat(resultProduct2).isNotNull()
            assertThat(resultProduct2!!.stock).isEqualTo(originalQuantity - 10) // DB에서 가져온 값 (재고 감소 확인)

            assertThat(resultProduct3).isNotNull()
            assertThat(resultProduct3!!.price).isEqualTo(Money.krw(3000)) // 캐시된 값

            // then - product2의 detail 캐시가 다시 저장되었는지 확인
            val product2CachedAgain = cacheTemplate.get(
                product2CacheKey,
                object : com.fasterxml.jackson.core.type.TypeReference<CachedProductDetailV1>() {},
            )
            assertThat(product2CachedAgain).isNotNull()
            assertThat(product2CachedAgain!!.stockQuantity).isEqualTo(originalQuantity - 10)
        }

        @Test
        @DisplayName("목록 캐시 히트 시 상품 순서가 유지된다")
        fun `maintain product order when list cache hits with partial detail cache`() {
            // given - 상품 3개 생성 (최신순)
            val product1 = createProduct(name = "첫번째")
            val product2 = createProduct(name = "두번째")
            val product3 = createProduct(name = "세번째")

            val criteria = ProductCriteria.FindProducts(
                page = 0,
                size = 20,
                sort = ProductSortType.LATEST,
                brandId = null,
            )

            // given - 첫 번째 조회
            val firstResult = productFacade.findProducts(criteria)
            val firstOrderIds = firstResult.products.take(3).map { it.productId }

            // given - product2의 detail 캐시만 삭제
            cacheTemplate.evict(ProductCacheKeys.ProductDetail(productId = product2.id))

            // when - 두 번째 조회
            val secondResult = productFacade.findProducts(criteria)
            val secondOrderIds = secondResult.products.take(3).map { it.productId }

            // then - 순서가 동일하게 유지됨
            assertThat(secondOrderIds).isEqualTo(firstOrderIds)
        }
    }

    private fun createProduct(
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
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
            brand = brand,
        )
        val savedProduct = productRepository.save(product)
        stockRepository.save(Stock.create(savedProduct.id, stockQuantity))
        productStatisticRepository.save(ProductStatistic.create(savedProduct.id))
        return savedProduct
    }
}
