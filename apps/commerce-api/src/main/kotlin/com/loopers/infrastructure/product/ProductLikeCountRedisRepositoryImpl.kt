package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductLikeCountRedisRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository

@Repository
class ProductLikeCountRedisRepositoryImpl(
    private val redisTemplate: RedisTemplate<String, String>,
) : ProductLikeCountRedisRepository {
    companion object {
        private const val LIKE_COUNT_KEY_PREFIX = "product:like:count:"

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
                return -1
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

        private val INIT_AND_DECREMENT_IF_POSITIVE_SCRIPT = RedisScript.of(
            """
            local exists = redis.call('EXISTS', KEYS[1])
            if exists == 0 then
                redis.call('SET', KEYS[1], ARGV[1])
            end
            local current = tonumber(redis.call('GET', KEYS[1]))
            if current <= 0 then
                return 0
            end
            redis.call('DECR', KEYS[1])
            return current - 1
            """.trimIndent(),
            Long::class.java,
        )
    }

    override fun incrementIfExists(productId: Long): Long? {
        val key = buildKey(productId)
        return redisTemplate.execute(INCREMENT_IF_EXISTS_SCRIPT, listOf(key))
    }

    override fun initAndIncrement(
        productId: Long,
        initialValue: Long,
    ): Long? {
        val key = buildKey(productId)
        return redisTemplate.execute(
            INIT_AND_INCREMENT_SCRIPT,
            listOf(key),
            initialValue.toString(),
        )
    }

    override fun decrementIfPositive(productId: Long): Long? {
        val key = buildKey(productId)
        return redisTemplate.execute(DECREMENT_IF_POSITIVE_SCRIPT, listOf(key))
    }

    override fun initAndDecrementIfPositive(
        productId: Long,
        initialValue: Long,
    ): Long? {
        val key = buildKey(productId)
        return redisTemplate.execute(
            INIT_AND_DECREMENT_IF_POSITIVE_SCRIPT,
            listOf(key),
            initialValue.toString(),
        )
    }

    override fun get(productId: Long): Long? {
        val key = buildKey(productId)
        return redisTemplate.opsForValue().get(key)?.toLongOrNull()
    }

    override fun setIfAbsent(
        productId: Long,
        value: Long,
    ): Boolean {
        val key = buildKey(productId)
        return redisTemplate.opsForValue().setIfAbsent(key, value.toString()) ?: false
    }

    override fun getAfterSetIfAbsent(productId: Long): Long? {
        val key = buildKey(productId)
        return redisTemplate.opsForValue().get(key)?.toLongOrNull()
    }

    override fun getAllKeys(): Set<String> = redisTemplate.scan(
        ScanOptions.scanOptions().match("$LIKE_COUNT_KEY_PREFIX*").build(),
    ).use { cursor -> cursor.asSequence().toSet() }

    override fun extractProductId(key: String): Long? =
        key.removePrefix(LIKE_COUNT_KEY_PREFIX).toLongOrNull()

    private fun buildKey(productId: Long): String = "$LIKE_COUNT_KEY_PREFIX$productId"
}
