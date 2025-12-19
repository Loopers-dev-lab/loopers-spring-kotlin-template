package com.loopers.infrastructure.product

import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate

@SpringBootTest
@DisplayName("ProductCacheRepository 통합 테스트")
class ProductCacheRepositoryIntegrationTest @Autowired constructor(
    private val productCacheRepository: ProductCacheRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("evictProductCache()")
    @Nested
    inner class EvictProductCache {

        @DisplayName("존재하는 캐시 키를 삭제한다")
        @Test
        fun `deletes existing cache key for given productId`() {
            // given
            val productId = 1L
            val cacheKey = "product:$productId"
            redisTemplate.opsForValue().set(cacheKey, "cached-value")

            // when
            productCacheRepository.evictProductCache(productId)

            // then
            val result = redisTemplate.hasKey(cacheKey)
            assertThat(result).isFalse()
        }

        @DisplayName("존재하지 않는 캐시 키 삭제 시 에러가 발생하지 않는다")
        @Test
        fun `does not throw error when deleting non-existing cache key`() {
            // given
            val productId = 999L

            // when & then - no exception should be thrown
            productCacheRepository.evictProductCache(productId)
        }
    }
}
