package com.loopers.domain.cache

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * 상품 캐시 무효화 서비스
 * - commerce-api의 상품 캐시를 무효화
 */
@Service
class ProductCacheService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PRODUCT_CACHE_PREFIX = "product:"
        private const val PRODUCT_LIST_CACHE_PREFIX = "products:"
    }

    /**
     * 특정 상품 캐시 무효화
     */
    fun invalidateProductCache(productId: Long) {
        try {
            val key = "$PRODUCT_CACHE_PREFIX$productId"
            val deleted = redisTemplate.delete(key)
            if (deleted) {
                logger.info("상품 캐시 무효화 성공: productId=$productId")
            } else {
                logger.debug("상품 캐시 없음: productId=$productId")
            }
        } catch (e: Exception) {
            logger.error("상품 캐시 무효화 실패: productId=$productId", e)
        }
    }

    /**
     * 상품 목록 캐시 전체 무효화
     * - 재고 소진 시 목록에서도 제거되어야 하므로
     * - SCAN 명령어 사용 (KEYS는 Redis를 블로킹하므로 프로덕션에서 위험)
     */
    fun invalidateProductListCache() {
        try {
            val pattern = "$PRODUCT_LIST_CACHE_PREFIX*"
            var deletedCount = 0L

            redisTemplate.execute { connection ->
                val scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build()

                val cursor = connection.scan(scanOptions)
                while (cursor.hasNext()) {
                    val key = String(cursor.next())
                    if (redisTemplate.delete(key)) {
                        deletedCount++
                    }
                }
                cursor.close()
                null
            }

            if (deletedCount > 0) {
                logger.info("상품 목록 캐시 무효화 성공: ${deletedCount}개")
            } else {
                logger.debug("상품 목록 캐시 없음")
            }
        } catch (e: Exception) {
            logger.error("상품 목록 캐시 무효화 실패", e)
        }
    }
}
