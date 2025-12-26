package com.loopers.application.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class ProductCacheKeysTest {

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
