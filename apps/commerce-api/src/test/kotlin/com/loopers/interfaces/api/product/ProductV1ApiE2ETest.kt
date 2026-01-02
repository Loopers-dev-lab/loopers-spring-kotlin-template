package com.loopers.interfaces.api.product

import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.infrastructure.ranking.RankingKeyGenerator
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val redisTemplate: RedisTemplate<String, String>,
    private val rankingKeyGenerator: RankingKeyGenerator,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    inner class GetProducts {

        @DisplayName("상품 목록을 조회하면 200 OK와 상품 목록을 반환한다")
        @Test
        fun returnProductList_whenRequestIsValid() {
            // given
            val brand = createBrand()
            val product1 = createProduct(brand = brand, name = "상품1")
            val product2 = createProduct(brand = brand, name = "상품2")

            // when
            val response = getProducts()

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.products).hasSize(2) },
                { assertThat(response.body?.data?.products?.map { it.id }).containsExactlyInAnyOrder(product1.id, product2.id) },
            )
        }

        @DisplayName("brandId로 필터링하면 해당 브랜드의 상품만 조회된다")
        @Test
        fun returnFilteredProducts_whenBrandIdIsProvided() {
            // given
            val brand1 = createBrand(name = "브랜드1")
            val brand2 = createBrand(name = "브랜드2")
            val product1 = createProduct(brand = brand1, name = "브랜드1 상품")
            createProduct(brand = brand2, name = "브랜드2 상품")

            // when
            val response = getProducts(brandId = brand1.id)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.products).hasSize(1) },
                { assertThat(response.body?.data?.products?.first()?.id).isEqualTo(product1.id) },
                { assertThat(response.body?.data?.products?.first()?.brandId).isEqualTo(brand1.id) },
            )
        }

        @DisplayName("페이지네이션이 정상 동작한다")
        @Test
        fun returnPaginatedProducts_whenPageParametersAreProvided() {
            // given
            val brand = createBrand()
            repeat(5) { index ->
                createProduct(brand = brand, name = "상품${index + 1}")
            }

            // when
            val firstPage = getProducts(page = 0, size = 2)
            val secondPage = getProducts(page = 1, size = 2)

            // then
            assertAll(
                { assertThat(firstPage.body?.data?.products).hasSize(2) },
                { assertThat(firstPage.body?.data?.hasNext).isTrue() },
                { assertThat(secondPage.body?.data?.products).hasSize(2) },
                { assertThat(secondPage.body?.data?.hasNext).isTrue() },
            )
        }

        @DisplayName("상품이 없으면 빈 목록을 반환한다")
        @Test
        fun returnEmptyList_whenNoProductsExist() {
            // when
            val response = getProducts()

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.products).isEmpty() },
                { assertThat(response.body?.data?.hasNext).isFalse() },
            )
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    inner class GetProduct {

        @DisplayName("상품 ID로 조회하면 200 OK와 상품 상세 정보를 반환한다")
        @Test
        fun returnProductDetail_whenProductExists() {
            // given
            val brand = createBrand(name = "테스트 브랜드")
            val product = createProduct(
                brand = brand,
                name = "테스트 상품",
                price = Money.krw(10000),
                stockQuantity = 100,
            )

            // when
            val response = getProduct(product.id)

            // then - 응답 구조가 평탄화됨 (data.product.* -> data.*)
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(product.id) },
                { assertThat(response.body?.data?.name).isEqualTo("테스트 상품") },
                { assertThat(response.body?.data?.price).isEqualTo(10000) },
                { assertThat(response.body?.data?.stock).isEqualTo(100) },
                { assertThat(response.body?.data?.brandId).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.brandName).isEqualTo("테스트 브랜드") },
                { assertThat(response.body?.data?.likeCount).isEqualTo(0) },
                { assertThat(response.body?.data?.rank).isNull() },
            )
        }

        @DisplayName("존재하지 않는 상품 ID로 조회하면 404 Not Found를 반환한다")
        @Test
        fun returnNotFound_whenProductDoesNotExist() {
            // when
            val response = getProduct(999L)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }

        @DisplayName("X-USER-ID 헤더와 함께 상품 조회하면 200 OK와 상품 상세 정보를 반환한다")
        @Test
        fun `returnProductDetail when X-USER-ID header is provided`() {
            // given
            val brand = createBrand(name = "테스트 브랜드")
            val product = createProduct(
                brand = brand,
                name = "테스트 상품",
                price = Money.krw(10000),
                stockQuantity = 100,
            )
            val userId = 1L

            // when
            val response = getProduct(productId = product.id, userId = userId)

            // then - 응답 구조가 평탄화됨 (data.product.* -> data.*)
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(product.id) },
                { assertThat(response.body?.data?.name).isEqualTo("테스트 상품") },
            )
        }

        @DisplayName("랭킹 데이터가 있으면 rank 필드에 순위가 반환된다")
        @Test
        fun `returnProductDetail with rank when ranking data exists`() {
            // given
            val brand = createBrand(name = "테스트 브랜드")
            val product1 = createProduct(brand = brand, name = "1등 상품")
            val product2 = createProduct(brand = brand, name = "2등 상품")

            val bucketKey = rankingKeyGenerator.currentBucketKey(RankingPeriod.HOURLY)
            redisTemplate.opsForZSet().add(bucketKey, product1.id.toString(), 100.0)
            redisTemplate.opsForZSet().add(bucketKey, product2.id.toString(), 50.0)

            // when
            val response1 = getProduct(product1.id)
            val response2 = getProduct(product2.id)

            // then
            assertAll(
                { assertThat(response1.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response1.body?.data?.id).isEqualTo(product1.id) },
                { assertThat(response1.body?.data?.rank).isEqualTo(1) },
                { assertThat(response2.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response2.body?.data?.id).isEqualTo(product2.id) },
                { assertThat(response2.body?.data?.rank).isEqualTo(2) },
            )
        }
    }

    private fun createBrand(name: String = "테스트 브랜드"): Brand {
        return brandRepository.save(Brand.create(name))
    }

    private fun createProduct(
        brand: Brand,
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
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

    private fun getProducts(
        brandId: Long? = null,
        sort: ProductSortType? = null,
        page: Int? = null,
        size: Int? = null,
    ): ResponseEntity<ApiResponse<ProductV1Response.GetProducts>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
        }

        val queryParams = buildString {
            val params = mutableListOf<String>()
            brandId?.let { params.add("brandId=$it") }
            sort?.let { params.add("sort=$it") }
            page?.let { params.add("page=$it") }
            size?.let { params.add("size=$it") }
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }

        return testRestTemplate.exchange(
            "/api/v1/products$queryParams",
            HttpMethod.GET,
            HttpEntity(null, headers),
            object : ParameterizedTypeReference<ApiResponse<ProductV1Response.GetProducts>>() {},
        )
    }

    private fun getProduct(
        productId: Long,
        userId: Long? = null,
    ): ResponseEntity<ApiResponse<ProductV1Response.GetProduct>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            userId?.let { set("X-USER-ID", it.toString()) }
        }

        return testRestTemplate.exchange(
            "/api/v1/products/$productId",
            HttpMethod.GET,
            HttpEntity(null, headers),
            object : ParameterizedTypeReference<ApiResponse<ProductV1Response.GetProduct>>() {},
        )
    }
}
