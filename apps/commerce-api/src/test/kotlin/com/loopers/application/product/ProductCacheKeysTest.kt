package com.loopers.application.product

import com.loopers.domain.product.PageQuery
import com.loopers.domain.product.ProductSortType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Duration

class ProductCacheKeysTest {

    @DisplayName("ProductList 캐시 키 테스트")
    @Nested
    inner class ProductListTest {

        @Test
        @DisplayName("캐시 키가 올바르게 생성된다")
        fun `generate cache key correctly`() {
            // given
            val pageQuery = createPageQuery(
                sort = ProductSortType.LATEST,
                brandId = 100L,
                page = 0,
                size = 20,
            )

            // when
            val cacheKey = ProductCacheKeys.ProductList.from(pageQuery)

            // then
            assertThat(cacheKey.key).isEqualTo("product-list:v1:LATEST:100:0:20")
            assertThat(cacheKey.traceKey).isEqualTo("product-list")
            assertThat(cacheKey.ttl).isEqualTo(Duration.ofSeconds(60))
        }

        @Test
        @DisplayName("brandId가 null이면 '_'로 표시된다")
        fun `use underscore when brandId is null`() {
            // given
            val pageQuery = createPageQuery(
                sort = ProductSortType.LIKES_DESC,
                brandId = null,
                page = 1,
                size = 20,
            )

            // when
            val cacheKey = ProductCacheKeys.ProductList.from(pageQuery)

            // then
            assertThat(cacheKey.key).isEqualTo("product-list:v1:LIKES_DESC:_:1:20")
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 1, 2])
        @DisplayName("page가 0, 1, 2일 때 shouldCache()가 true를 반환한다")
        fun `return true when page is within cache threshold`(page: Int) {
            // given
            val cacheKey = createProductListCacheKey(page = page)

            // when
            val shouldCache = cacheKey.shouldCache()

            // then
            assertThat(shouldCache).isTrue
        }

        @ParameterizedTest
        @ValueSource(ints = [3, 4, 10, 100])
        @DisplayName("page가 3 이상일 때 shouldCache()가 false를 반환한다")
        fun `return false when page exceeds cache threshold`(page: Int) {
            // given
            val cacheKey = createProductListCacheKey(page = page)

            // when
            val shouldCache = cacheKey.shouldCache()

            // then
            assertThat(shouldCache).isFalse
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

    private fun createPageQuery(
        sort: ProductSortType = ProductSortType.LATEST,
        brandId: Long? = null,
        page: Int = 0,
        size: Int = 20,
    ): PageQuery {
        return PageQuery.of(
            page = page,
            size = size,
            sort = sort,
            brandId = brandId,
        )
    }

    private fun createProductListCacheKey(
        sort: ProductSortType = ProductSortType.LATEST,
        brandId: Long? = null,
        page: Int = 0,
        size: Int = 20,
    ): ProductCacheKeys.ProductList {
        return ProductCacheKeys.ProductList.from(
            createPageQuery(
                sort = sort,
                brandId = brandId,
                page = page,
                size = size,
            ),
        )
    }
}
