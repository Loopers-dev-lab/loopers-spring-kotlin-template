package com.loopers.domain.product

import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
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

        /**
         * Redis에서 원자적으로 증가하는 Lua 스크립트 (키가 존재하는 경우)
         * KEYS[1]: 카운터 키
         * 반환값: 증가 후의 값, 또는 키가 없으면 -1
         */
        private val INCREMENT_IF_EXISTS_SCRIPT = RedisScript.of(
            """
            local current = redis.call('GET', KEYS[1])
            if current == false then
                return -1
            end
            redis.call('INCR', KEYS[1])
            return tonumber(current) + 1
            """.trimIndent(),
            Long::class.java,
        )

        /**
         * Redis에서 원자적으로 초기화 후 증가하는 Lua 스크립트
         * KEYS[1]: 카운터 키
         * ARGV[1]: 초기값
         * 반환값: 증가 후의 값
         */
        private val INIT_AND_INCREMENT_SCRIPT = RedisScript.of(
            """
            redis.call('SET', KEYS[1], ARGV[1])
            redis.call('INCR', KEYS[1])
            return tonumber(ARGV[1]) + 1
            """.trimIndent(),
            Long::class.java,
        )

        /**
         * Redis에서 원자적으로 감소하되 0 이하로 내려가지 않도록 하는 Lua 스크립트
         * KEYS[1]: 카운터 키
         * 반환값: 감소 후의 값 (0 이하면 감소하지 않고 0 반환)
         */
        private val DECREMENT_IF_POSITIVE_SCRIPT = RedisScript.of(
            """
            local current = redis.call('GET', KEYS[1])
            if current == false then
                return 0
            end
            current = tonumber(current)
            if current <= 0 then
                return 0
            end
            redis.call('DECR', KEYS[1])
            return current - 1
            """.trimIndent(),
            Long::class.java,
        )
    }

    /**
     * 좋아요 수를 원자적으로 증가시킵니다.
     * Lua 스크립트를 사용하여 원자적으로 증가하며, 키가 없으면 DB에서 초기값을 가져옵니다.
     * Redis 장애 시 DB로 fallback 처리합니다.
     * @return 증가 후의 좋아요 수
     */
    @Transactional
    fun increment(productId: Long): Long {
        try {
            val key = getLikeCountKey(productId)

            // 1단계: 키가 존재하면 바로 증가 (대부분의 경우)
            val result = redisTemplate.execute(INCREMENT_IF_EXISTS_SCRIPT, listOf(key))
            if (result != null && result != -1L) {
                return result
            }

            // 2단계: 키가 없으면 DB에서 초기값을 가져와 초기화 후 증가
            val initialValue = productRepository.findById(productId)?.likeCount ?: 0L
            return redisTemplate.execute(
                INIT_AND_INCREMENT_SCRIPT,
                listOf(key),
                initialValue.toString(),
            ) ?: 0L
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
     * Lua 스크립트를 사용하여 원자적으로 감소하되 0 이하로 내려가지 않도록 보장합니다.
     * Redis 장애 시 DB로 fallback 처리합니다.
     * @return 감소 후의 좋아요 수 (0 이하로 내려가지 않음)
     */
    @Transactional
    fun decrement(productId: Long): Long {
        try {
            val key = getLikeCountKey(productId)

            // Lua 스크립트를 사용하여 원자적으로 감소 (0 이하로 내려가지 않음)
            // 키가 없거나 0이면 0을 반환
            return redisTemplate.execute(
                DECREMENT_IF_POSITIVE_SCRIPT,
                listOf(key),
            ) ?: 0L
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
    @Transactional
    fun syncToDatabase(productId: Long): Boolean {
        val key = getLikeCountKey(productId)
        val redisCount = redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: return false

        val product = productRepository.findByIdWithLock(productId) ?: return false
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
