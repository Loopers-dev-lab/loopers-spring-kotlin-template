package com.loopers.domain.product

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.application.product.ProductCacheKeys
import com.loopers.cache.CacheTemplate
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@DisplayName("ProductCacheService 통합 테스트")
class ProductCacheServiceIntegrationTest @Autowired constructor(
    private val productCacheService: ProductCacheService,
    private val cacheTemplate: CacheTemplate,
    private val redisCleanUp: RedisCleanUp,
) {

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("evictStockDepletedProducts 테스트")
    @Nested
    inner class EvictStockDepletedProducts {

        @Test
        @DisplayName("빈 커맨드 시 아무 작업도 하지 않는다")
        fun `does nothing when command is empty`() {
            // given
            val productId = 100L
            val cacheKey = ProductCacheKeys.ProductDetail(productId)
            val cachedData = createCachedProductData(productId)
            cacheTemplate.put(cacheKey, cachedData)

            val command = EvictStockDepletedCommand(productIds = emptyList())

            // when
            productCacheService.evictStockDepletedProducts(command)

            // then - 캐시가 유지되어야 함
            val cacheAfter = cacheTemplate.get(
                cacheKey,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cacheAfter).isNotNull
        }

        @Test
        @DisplayName("단일 상품의 캐시가 무효화된다")
        fun `evicts cache for single product`() {
            // given
            val productId = 100L
            val cacheKey = ProductCacheKeys.ProductDetail(productId)
            val cachedData = createCachedProductData(productId)
            cacheTemplate.put(cacheKey, cachedData)

            // given - 캐시가 존재하는지 확인
            val cacheBefore = cacheTemplate.get(
                cacheKey,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cacheBefore).isNotNull

            val command = EvictStockDepletedCommand(productIds = listOf(productId))

            // when
            productCacheService.evictStockDepletedProducts(command)

            // then
            val cacheAfter = cacheTemplate.get(
                cacheKey,
                object : TypeReference<Map<String, Any>>() {},
            )
            assertThat(cacheAfter).isNull()
        }

        @Test
        @DisplayName("여러 상품의 캐시가 배치로 무효화된다")
        fun `evicts cache for multiple products in batch`() {
            // given
            val productId1 = 100L
            val productId2 = 200L
            val productId3 = 300L

            val cacheKey1 = ProductCacheKeys.ProductDetail(productId1)
            val cacheKey2 = ProductCacheKeys.ProductDetail(productId2)
            val cacheKey3 = ProductCacheKeys.ProductDetail(productId3)

            cacheTemplate.put(cacheKey1, createCachedProductData(productId1))
            cacheTemplate.put(cacheKey2, createCachedProductData(productId2))
            cacheTemplate.put(cacheKey3, createCachedProductData(productId3))

            val command = EvictStockDepletedCommand(productIds = listOf(productId1, productId2))

            // when
            productCacheService.evictStockDepletedProducts(command)

            // then - productId1, productId2의 캐시만 무효화됨
            val cache1 = cacheTemplate.get(cacheKey1, object : TypeReference<Map<String, Any>>() {})
            val cache2 = cacheTemplate.get(cacheKey2, object : TypeReference<Map<String, Any>>() {})
            val cache3 = cacheTemplate.get(cacheKey3, object : TypeReference<Map<String, Any>>() {})

            assertThat(cache1).isNull()
            assertThat(cache2).isNull()
            assertThat(cache3).isNotNull() // 이 캐시는 유지됨
        }

        @Test
        @DisplayName("존재하지 않는 캐시 키에 대해서도 정상 동작한다")
        fun `works correctly for non-existing cache keys`() {
            // given
            val nonExistingProductId = 999L
            val command = EvictStockDepletedCommand(productIds = listOf(nonExistingProductId))

            // when & then - 예외가 발생하지 않아야 함
            productCacheService.evictStockDepletedProducts(command)
        }
    }

    // ===========================================
    // Helper methods
    // ===========================================

    private fun createCachedProductData(productId: Long): Map<String, Any> {
        return mapOf(
            "productId" to productId,
            "name" to "Test Product $productId",
            "price" to 10000L,
            "stock" to 100,
        )
    }
}
