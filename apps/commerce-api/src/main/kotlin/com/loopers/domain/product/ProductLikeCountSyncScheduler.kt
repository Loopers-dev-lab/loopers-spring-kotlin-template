package com.loopers.domain.product

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductLikeCountSyncScheduler(
    private val redisTemplate: RedisTemplate<String, String>,
    private val productRepository: ProductRepository,
) {
    companion object {
        private const val LIKE_COUNT_KEY_PREFIX = "product:like:count:"
        private const val SYNC_INTERVAL_MS = 300000L
        private val logger = LoggerFactory.getLogger(ProductLikeCountSyncScheduler::class.java)
    }

    @Scheduled(fixedDelay = SYNC_INTERVAL_MS)
    @Transactional
    fun syncLikeCountsToDatabase() {
        runCatching {
            val keys = getAllLikeCountKeys() ?: return
            if (keys.isEmpty()) return

            val result = syncAllKeys(keys)
            logSyncResult(keys.size, result)
        }.onFailure { e ->
            logger.error("Error during like count sync: ${e.message}", e)
        }
    }

    private fun getAllLikeCountKeys(): Set<String>? =
        redisTemplate.keys("$LIKE_COUNT_KEY_PREFIX*")

    private fun syncAllKeys(keys: Set<String>): SyncResult {
        var successCount = 0
        var failCount = 0

        keys.forEach { key ->
            if (syncSingleKey(key)) {
                successCount++
            } else {
                failCount++
            }
        }

        return SyncResult(successCount, failCount)
    }

    private fun syncSingleKey(key: String): Boolean =
        runCatching {
            val productId = extractProductId(key) ?: return false
            val likeCount = getLikeCountFromRedis(key) ?: return false
            updateProductLikeCount(productId, likeCount)
            true
        }.getOrElse { e ->
            logger.warn("Failed to sync like count for key $key: ${e.message}")
            false
        }

    private fun extractProductId(key: String): Long? =
        key.removePrefix(LIKE_COUNT_KEY_PREFIX).toLongOrNull()

    private fun getLikeCountFromRedis(key: String): Long? =
        redisTemplate.opsForValue().get(key)?.toLongOrNull()

    private fun updateProductLikeCount(productId: Long, likeCount: Long) {
        val product = productRepository.findByIdWithLock(productId) ?: return
        product.setLikeCount(likeCount)
        productRepository.save(product)
    }

    private fun logSyncResult(totalKeys: Int, result: SyncResult) {
        logger.info(
            "Like count sync completed: ${result.successCount} succeeded, ${result.failCount} failed (total: $totalKeys)",
        )
    }

    private data class SyncResult(
        val successCount: Int,
        val failCount: Int,
    )
}
