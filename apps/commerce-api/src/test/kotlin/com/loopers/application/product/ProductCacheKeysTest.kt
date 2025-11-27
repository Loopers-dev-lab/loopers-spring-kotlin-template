package com.loopers.application.product

import com.loopers.domain.product.ProductSortType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class ProductCacheKeysTest {

    @DisplayName("ProductList 캐시 키 테스트")
    @Nested
    inner class ProductListTest {

        @Test
        @DisplayName("캐시 키가 올바르게 생성된다")
        fun `generate cache key correctly`() {
            // given
            val sort = ProductSortType.LATEST
            val brandId = 100L
            val page = 0
            val size = 20

            // when
            val cacheKey = ProductCacheKeys.ProductList(
                sort = sort,
                brandId = brandId,
                page = page,
                size = size,
            )

            // then
            assertThat(cacheKey.key).isEqualTo("product-list:v1:LATEST:brand_100:0:20")
            assertThat(cacheKey.traceKey).isEqualTo("product-list")
            assertThat(cacheKey.ttl).isEqualTo(Duration.ofSeconds(60))
        }

        @Test
        @DisplayName("brandId가 null이면 '_'로 표시된다")
        fun `use underscore when brandId is null`() {
            // when
            val cacheKey = ProductCacheKeys.ProductList(
                sort = ProductSortType.LIKES_DESC,
                brandId = null,
                page = 1,
                size = 20,
            )

            // then
            assertThat(cacheKey.key).isEqualTo("product-list:v1:LIKES_DESC:_:1:20")
        }

        @Test
        @DisplayName("sort가 null이면 '_'로 표시된다")
        fun `use underscore when sort is null`() {
            // when
            val cacheKey = ProductCacheKeys.ProductList(
                sort = null,
                brandId = 200L,
                page = 2,
                size = 10,
            )

            // then
            assertThat(cacheKey.key).isEqualTo("product-list:v1:_:brand_200:2:10")
        }

        @Test
        @DisplayName("page가 0, 1, 2일 때 shouldCache()가 true를 반환한다")
        fun `return true when page is 0, 1, or 2`() {
            // when & then
            assertThat(ProductCacheKeys.ProductList(null, null, 0, 20).shouldCache()).isTrue
            assertThat(ProductCacheKeys.ProductList(null, null, 1, 20).shouldCache()).isTrue
            assertThat(ProductCacheKeys.ProductList(null, null, 2, 20).shouldCache()).isTrue
        }

        @Test
        @DisplayName("page가 3 이상일 때 shouldCache()가 false를 반환한다")
        fun `return false when page is 3 or more`() {
            // when & then
            assertThat(ProductCacheKeys.ProductList(null, null, 3, 20).shouldCache()).isFalse
            assertThat(ProductCacheKeys.ProductList(null, null, 10, 20).shouldCache()).isFalse
        }

        @Test
        @DisplayName("page가 null일 때 0으로 간주하여 shouldCache()가 true를 반환한다")
        fun `treat null page as 0 and return true`() {
            // when
            val cacheKey = ProductCacheKeys.ProductList(null, null, null, 20)

            // then
            assertThat(cacheKey.shouldCache()).isTrue
            assertThat(cacheKey.key).contains(":0:")
        }
    }

    @DisplayName("ProductDetail 캐시 키 테스트")
    @Nested
    inner class ProductDetailTest {

        @Test
        @DisplayName("캐시 키가 올바르게 생성된다")
        fun `generate cache key correctly`() {
            // given
            val productId = 12345L

            // when
            val cacheKey = ProductCacheKeys.ProductDetail(productId = productId)

            // then
            assertThat(cacheKey.key).isEqualTo("product-detail:v1:12345")
            assertThat(cacheKey.traceKey).isEqualTo("product-detail")
            assertThat(cacheKey.ttl).isEqualTo(Duration.ofSeconds(60))
        }

        @Test
        @DisplayName("서로 다른 productId는 서로 다른 키를 생성한다")
        fun `different productIds generate different keys`() {
            // when
            val key1 = ProductCacheKeys.ProductDetail(productId = 1L)
            val key2 = ProductCacheKeys.ProductDetail(productId = 2L)

            // then
            assertThat(key1.key).isNotEqualTo(key2.key)
        }
    }
}
