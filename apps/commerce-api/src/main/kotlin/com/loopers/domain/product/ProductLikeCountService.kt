package com.loopers.domain.product

import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Redis를 사용한 상품 좋아요 카운트 관리 서비스
 *
 * Redis의 atomic 연산(INCR, DECR)을 활용하여 동시성 문제를 해결합니다.
 * - 모든 좋아요 증감은 Redis에서 먼저 처리
 * - Redis 장애 시 DB로 fallback
 * - 주기적으로 또는 특정 이벤트 시점에 DB와 동기화
 */
@Service
class ProductLikeCountService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val productRepository: ProductRepository,
) {
    companion object {
        private const val LIKE_COUNT_KEY_PREFIX = "product:like:count:"
        private val logger = LoggerFactory.getLogger(ProductLikeCountService::class.java)
    }

    /**
     * 좋아요 수를 원자적으로 증가시킵니다.
     * Redis 장애 시 DB로 fallback 처리합니다.
     * @return 증가 후의 좋아요 수
     */
    @Transactional
    fun increment(productId: Long): Long {
        try {
            val key = getLikeCountKey(productId)

            // Redis에 키가 없으면 DB에서 가져와서 초기화
            if (!redisTemplate.hasKey(key)) {
                initializeLikeCount(productId)
            }

            // Redis에서 원자적으로 증가
            return redisTemplate.opsForValue().increment(key) ?: 0L
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed, falling back to DB for increment: productId=$productId", e)
            return fallbackToDbIncrement(productId)
        } catch (e: Exception) {
            logger.error("Redis operation failed, falling back to DB for increment: productId=$productId", e)
            return fallbackToDbIncrement(productId)
        }
    }

    /**
     * 좋아요 수를 원자적으로 감소시킵니다.
     * Redis 장애 시 DB로 fallback 처리합니다.
     * @return 감소 후의 좋아요 수 (0 이하로 내려가지 않음)
     */
    @Transactional
    fun decrement(productId: Long): Long {
        try {
            val key = getLikeCountKey(productId)

            // Redis에 키가 없으면 DB에서 가져와서 초기화
            if (!redisTemplate.hasKey(key)) {
                initializeLikeCount(productId)
            }

            // Redis에서 원자적으로 감소 (0 이하로 내려가지 않도록 보장)
            val currentCount = getLikeCount(productId)
            if (currentCount <= 0) {
                return 0L
            }

            return redisTemplate.opsForValue().decrement(key)?.coerceAtLeast(0L) ?: 0L
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed, falling back to DB for decrement: productId=$productId", e)
            return fallbackToDbDecrement(productId)
        } catch (e: Exception) {
            logger.error("Redis operation failed, falling back to DB for decrement: productId=$productId", e)
            return fallbackToDbDecrement(productId)
        }
    }

    /**
     * 현재 좋아요 수를 조회합니다.
     * Redis를 우선적으로 확인하고, 없으면 DB에서 가져옵니다.
     * Redis 장애 시 DB에서 직접 조회합니다.
     * @return 현재 좋아요 수
     */
    fun getLikeCount(productId: Long): Long {
        try {
            val key = getLikeCountKey(productId)

            // Redis에서 조회
            val count = redisTemplate.opsForValue().get(key)?.toLongOrNull()
            if (count != null) {
                return count
            }

            // Redis에 없으면 DB에서 가져와서 캐싱
            return initializeLikeCount(productId)
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed, falling back to DB for getLikeCount: productId=$productId", e)
            return fallbackToDbGetLikeCount(productId)
        } catch (e: Exception) {
            logger.error("Redis operation failed, falling back to DB for getLikeCount: productId=$productId", e)
            return fallbackToDbGetLikeCount(productId)
        }
    }

    /**
     * Redis의 좋아요 수를 DB에 동기화합니다.
     * @return 동기화 성공 여부
     */
    fun syncToDatabase(productId: Long): Boolean {
        val key = getLikeCountKey(productId)
        val redisCount = redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: return false

        val product = productRepository.findById(productId) ?: return false
        product.setLikeCount(redisCount)
        productRepository.save(product)

        return true
    }

    /**
     * 여러 상품의 좋아요 수를 DB에 동기화합니다.
     */
    fun syncMultipleToDatabase(productIds: List<Long>) {
        productIds.forEach { productId ->
            try {
                syncToDatabase(productId)
            } catch (e: Exception) {
                // 로깅 후 계속 진행
                println("Failed to sync like count for product $productId: ${e.message}")
            }
        }
    }

    /**
     * DB에서 좋아요 수를 가져와 Redis에 초기화합니다.
     */
    private fun initializeLikeCount(productId: Long): Long {
        val product = productRepository.findById(productId)
        val likeCount = product?.likeCount ?: 0L

        val key = getLikeCountKey(productId)
        redisTemplate.opsForValue().set(key, likeCount.toString())

        return likeCount
    }

    /**
     * Redis 캐시를 무효화합니다.
     */
    fun evictCache(productId: Long) {
        try {
            val key = getLikeCountKey(productId)
            redisTemplate.delete(key)
        } catch (e: Exception) {
            logger.warn("Failed to evict Redis cache for productId=$productId", e)
        }
    }

    /**
     * Redis 장애 시 DB를 통한 좋아요 수 증가 fallback
     * 비관적 락을 사용하여 동시성 문제를 방지합니다.
     */
    private fun fallbackToDbIncrement(productId: Long): Long {
        val product = productRepository.findByIdWithLock(productId)
            ?: throw IllegalArgumentException("Product not found: $productId")

        val newCount = product.likeCount + 1
        product.setLikeCount(newCount)
        productRepository.save(product)

        return newCount
    }

    /**
     * Redis 장애 시 DB를 통한 좋아요 수 감소 fallback
     * 비관적 락을 사용하여 동시성 문제를 방지합니다.
     */
    private fun fallbackToDbDecrement(productId: Long): Long {
        val product = productRepository.findByIdWithLock(productId)
            ?: throw IllegalArgumentException("Product not found: $productId")

        val newCount = maxOf(0, product.likeCount - 1)
        product.setLikeCount(newCount)
        productRepository.save(product)

        return newCount
    }

    /**
     * Redis 장애 시 DB를 통한 좋아요 수 조회 fallback
     */
    private fun fallbackToDbGetLikeCount(productId: Long): Long {
        val product = productRepository.findById(productId)
        return product?.likeCount ?: 0L
    }

    private fun getLikeCountKey(productId: Long): String = "$LIKE_COUNT_KEY_PREFIX$productId"
}
