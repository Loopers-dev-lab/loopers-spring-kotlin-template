package com.loopers.domain.product

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Redis의 좋아요 카운트를 주기적으로 DB에 동기화하는 스케줄러
 *
 * 주기적으로 Redis에 있는 모든 좋아요 카운트를 DB에 반영합니다.
 * - 매 5분마다 실행
 * - Redis와 DB의 데이터 정합성 유지
 */
@Component
class ProductLikeCountSyncScheduler(
    private val redisTemplate: RedisTemplate<String, String>,
    private val productRepository: ProductRepository,
) {
    companion object {
        private const val LIKE_COUNT_KEY_PREFIX = "product:like:count:"
    }

    /**
     * 매 5분마다 Redis의 좋아요 카운트를 DB에 동기화
     */
    @Scheduled(fixedDelay = 300000) // 5분 = 300,000ms
    @Transactional
    fun syncLikeCountsToDatabase() {
        try {
            // Redis에서 모든 좋아요 카운트 키 조회
            val keys = redisTemplate.keys("$LIKE_COUNT_KEY_PREFIX*") ?: return

            if (keys.isEmpty()) {
                return
            }

            println("Starting like count sync for ${keys.size} products")

            var successCount = 0
            var failCount = 0

            keys.forEach { key ->
                try {
                    // 키에서 productId 추출
                    val productId = key.removePrefix(LIKE_COUNT_KEY_PREFIX).toLongOrNull()
                    if (productId == null) {
                        failCount++
                        return@forEach
                    }

                    // Redis에서 카운트 조회
                    val redisCount = redisTemplate.opsForValue().get(key)?.toLongOrNull()
                    if (redisCount == null) {
                        failCount++
                        return@forEach
                    }

                    // DB 업데이트
                    val product = productRepository.findById(productId)
                    if (product != null) {
                        product.setLikeCount(redisCount)
                        productRepository.save(product)
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    failCount++
                    println("Failed to sync like count for key $key: ${e.message}")
                }
            }

            println("Like count sync completed: $successCount succeeded, $failCount failed")
        } catch (e: Exception) {
            println("Error during like count sync: ${e.message}")
        }
    }
}
