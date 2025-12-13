package com.loopers.interfaces.api.like

import com.loopers.domain.product.Brand
import com.loopers.domain.product.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStatistic
import com.loopers.domain.product.ProductStatisticRepository
import com.loopers.domain.product.Stock
import com.loopers.domain.product.StockRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val brandRepository: BrandRepository,
    private val productStatisticRepository: ProductStatisticRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/like/products/{productId}")
    @Nested
    inner class AddLike {

        @DisplayName("상품에 좋아요를 추가하면 200 OK를 반환한다")
        @Test
        fun returnSuccess_whenAddingLike() {
            // given
            val userId = 1L
            val product = createProduct()

            // when
            val response = addLike(userId, product.id)

            // then - E2E 테스트 책임: HTTP 응답 검증
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }

        @DisplayName("동일한 상품에 중복으로 좋아요를 추가해도 200 OK를 반환한다 (멱등성)")
        @Test
        fun returnSuccess_whenAddingDuplicateLike() {
            // given
            val userId = 1L
            val product = createProduct()

            // when
            val firstResponse = addLike(userId, product.id)
            val secondResponse = addLike(userId, product.id)

            // then - E2E 테스트 책임: HTTP 응답 검증
            assertThat(firstResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(secondResponse.statusCode).isEqualTo(HttpStatus.OK)
        }

        @DisplayName("존재하지 않는 상품에 좋아요를 추가하면 404 Not Found를 반환한다")
        @Test
        fun returnNotFound_whenProductDoesNotExist() {
            // given
            val userId = 1L

            // when
            val response = addLike(userId, 999L)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("X-USER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        @Test
        fun returnBadRequest_whenUserIdHeaderIsMissing() {
            // given
            val product = createProduct()

            // when
            val response = addLike(null, product.id)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @DisplayName("DELETE /api/v1/like/products/{productId}")
    @Nested
    inner class RemoveLike {

        @DisplayName("상품 좋아요를 삭제하면 200 OK를 반환한다")
        @Test
        fun returnSuccess_whenRemovingLike() {
            // given
            val userId = 1L
            val product = createProduct()
            addLike(userId, product.id)

            // when
            val response = removeLike(userId, product.id)

            // then - E2E 테스트 책임: HTTP 응답 검증
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }

        @DisplayName("좋아요하지 않은 상품의 좋아요를 삭제해도 200 OK를 반환한다 (멱등성)")
        @Test
        fun returnSuccess_whenRemovingNonExistentLike() {
            // given
            val userId = 1L
            val product = createProduct()

            // when
            val response = removeLike(userId, product.id)

            // then - E2E 테스트 책임: HTTP 응답 검증
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        }

        @DisplayName("존재하지 않는 상품의 좋아요를 삭제하면 404 Not Found를 반환한다")
        @Test
        fun returnNotFound_whenProductDoesNotExist() {
            // given
            val userId = 1L

            // when
            val response = removeLike(userId, 999L)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("X-USER-ID 헤더가 없으면 400 Bad Request를 반환한다")
        @Test
        fun returnBadRequest_whenUserIdHeaderIsMissing() {
            // given
            val product = createProduct()

            // when
            val response = removeLike(null, product.id)

            // then
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    private fun createBrand(name: String = "테스트 브랜드"): Brand {
        return brandRepository.save(Brand.create(name))
    }

    private fun createProduct(
        name: String = "테스트 상품",
        price: Money = Money.krw(10000),
        stockQuantity: Int = 100,
    ): Product {
        val brand = createBrand()
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

    private fun addLike(userId: Long?, productId: Long): ResponseEntity<ApiResponse<Unit>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            userId?.let { set("X-USER-ID", it.toString()) }
        }

        return testRestTemplate.exchange(
            "/api/v1/like/products/$productId",
            HttpMethod.POST,
            HttpEntity(null, headers),
            object : ParameterizedTypeReference<ApiResponse<Unit>>() {},
        )
    }

    private fun removeLike(userId: Long?, productId: Long): ResponseEntity<ApiResponse<Unit>> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            userId?.let { set("X-USER-ID", it.toString()) }
        }

        return testRestTemplate.exchange(
            "/api/v1/like/products/$productId",
            HttpMethod.DELETE,
            HttpEntity(null, headers),
            object : ParameterizedTypeReference<ApiResponse<Unit>>() {},
        )
    }
}
