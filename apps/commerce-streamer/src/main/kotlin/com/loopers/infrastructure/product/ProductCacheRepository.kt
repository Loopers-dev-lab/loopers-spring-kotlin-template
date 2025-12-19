package com.loopers.infrastructure.product

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository

@Repository
class ProductCacheRepository(
    private val redisTemplate: RedisTemplate<String, String>,
) {
    companion object {
        private const val PRODUCT_CACHE_KEY_PREFIX = "product:"
    }

    fun evictProductCache(productId: Long) {
        redisTemplate.delete("$PRODUCT_CACHE_KEY_PREFIX$productId")
    }
}
