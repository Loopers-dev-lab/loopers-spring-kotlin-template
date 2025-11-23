package com.loopers.domain.product

import org.slf4j.LoggerFactory
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProductLikeCountService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val productRepository: ProductRepository,
) {
    companion object {
        private const val LIKE_COUNT_KEY_PREFIX = "product:like:count:"
        private const val KEY_NOT_FOUND = -1L
        private val logger = LoggerFactory.getLogger(ProductLikeCountService::class.java)

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

        private val INIT_AND_INCREMENT_SCRIPT = RedisScript.of(
            """
            local exists = redis.call('EXISTS', KEYS[1])
            if exists == 0 then
                redis.call('SET', KEYS[1], ARGV[1])
            end
            redis.call('INCR', KEYS[1])
            local result = redis.call('GET', KEYS[1])
            return tonumber(result)
            """.trimIndent(),
            Long::class.java,
        )

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

    @Transactional
    fun increment(productId: Long): Long =
        executeWithFallback(
            productId = productId,
            operation = { incrementInRedis(productId) },
            fallback = { incrementInDatabase(productId) },
        )

    @Transactional
    fun decrement(productId: Long): Long =
        executeWithFallback(
            productId = productId,
            operation = { decrementInRedis(productId) },
            fallback = { decrementInDatabase(productId) },
        )

    fun getLikeCount(productId: Long): Long =
        executeWithFallback(
            productId = productId,
            operation = { getFromRedisOrInitialize(productId) },
            fallback = { getFromDatabase(productId) },
        )

    private fun incrementInRedis(productId: Long): Long {
        val key = buildKey(productId)
        val result = redisTemplate.execute(INCREMENT_IF_EXISTS_SCRIPT, listOf(key))

        return if (isKeyExists(result)) {
            result!!
        } else {
            initializeAndIncrement(productId, key)
        }
    }

    private fun decrementInRedis(productId: Long): Long {
        val key = buildKey(productId)
        return redisTemplate.execute(DECREMENT_IF_POSITIVE_SCRIPT, listOf(key)) ?: 0L
    }

    private fun getFromRedisOrInitialize(productId: Long): Long {
        val key = buildKey(productId)
        val count = redisTemplate.opsForValue().get(key)?.toLongOrNull()

        return count ?: initializeFromDatabase(productId, key)
    }

    private fun initializeAndIncrement(productId: Long, key: String): Long {
        val initialValue = getFromDatabase(productId)
        return redisTemplate.execute(
            INIT_AND_INCREMENT_SCRIPT,
            listOf(key),
            initialValue.toString(),
        ) ?: 0L
    }

    private fun initializeFromDatabase(productId: Long, key: String): Long {
        val likeCount = getFromDatabase(productId)
        redisTemplate.opsForValue().setIfAbsent(key, likeCount.toString())
        return redisTemplate.opsForValue().get(key)?.toLongOrNull() ?: likeCount
    }

    private fun incrementInDatabase(productId: Long): Long =
        updateLikeCountWithLock(productId) { it + 1 }

    private fun decrementInDatabase(productId: Long): Long =
        updateLikeCountWithLock(productId) { maxOf(0, it - 1) }

    private fun updateLikeCountWithLock(
        productId: Long,
        update: (Long) -> Long,
    ): Long {
        val product = findProductWithLock(productId)
        val newCount = update(product.likeCount)
        product.setLikeCount(newCount)
        productRepository.save(product)
        return newCount
    }

    private fun getFromDatabase(productId: Long): Long =
        productRepository.findById(productId)?.likeCount ?: 0L

    private fun findProductWithLock(productId: Long): Product =
        productRepository.findByIdWithLock(productId)
            ?: throw IllegalArgumentException("Product not found: $productId")

    private fun executeWithFallback(
        productId: Long,
        operation: () -> Long,
        fallback: () -> Long,
    ): Long =
        try {
            operation()
        } catch (e: RedisConnectionFailureException) {
            logger.warn("Redis connection failed, falling back to DB: productId=$productId", e)
            fallback()
        } catch (e: Exception) {
            logger.error("Redis operation failed, falling back to DB: productId=$productId", e)
            fallback()
        }

    private fun isKeyExists(result: Long?): Boolean = result != null && result != KEY_NOT_FOUND

    private fun buildKey(productId: Long): String = "$LIKE_COUNT_KEY_PREFIX$productId"
}
