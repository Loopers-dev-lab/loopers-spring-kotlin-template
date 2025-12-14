package com.loopers.infrastructure.cache

import com.loopers.application.cache.ProductCache
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class ProductCacheImpl(
    private val redisTemplate: RedisTemplate<String, Any>,
) : ProductCache {

    private val log = LoggerFactory.getLogger(ProductCacheImpl::class.java)

    companion object {
        private const val VERSION = 1L
    }

    /**
     * 상품 상세 캐시 삭제 (모든 사용자)
     * 패턴: product:1:detail:{productId}:*
     */
    override fun evictProductDetail(productId: Long) {
        val pattern = "product:$VERSION:detail:$productId:*"
        try {
            val keys = redisTemplate.keys(pattern)
            if (!keys.isNullOrEmpty()) {
                redisTemplate.delete(keys)
                log.info("상품 상세 캐시 삭제: productId={}, 삭제된 키={}", productId, keys.size)
            } else {
                log.debug("삭제할 상품 상세 캐시 없음: productId={}", productId)
            }
        } catch (e: Exception) {
            log.error("상품 상세 캐시 삭제 실패: productId={}", productId, e)
        }
    }
}
