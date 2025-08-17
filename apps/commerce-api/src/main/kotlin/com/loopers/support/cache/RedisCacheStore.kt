package com.loopers.support.cache

import com.loopers.config.redis.RedisConfig.Companion.REDIS_TEMPLATE_MASTER
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class RedisCacheStore(
    private val redisTemplate: RedisTemplate<String, String>,
    @Qualifier(REDIS_TEMPLATE_MASTER)
    private val masterRedisTemplate: RedisTemplate<String, String>,
) : CacheStore {

    override fun get(key: String): String? =
        runCatching { redisTemplate.opsForValue().get(key) }.getOrNull()

    override fun getMaster(key: String): String? =
        runCatching { masterRedisTemplate.opsForValue().get(key) }.getOrNull()

    override fun set(key: String, value: String) {
        runCatching { masterRedisTemplate.opsForValue().set(key, value) }
    }

    override fun set(key: String, value: String, ttl: Duration) {
        runCatching {
            masterRedisTemplate.opsForValue().set(key, value, ttl.toMillis(), TimeUnit.MILLISECONDS)
        }
    }

    override fun delete(key: String) {
        runCatching { masterRedisTemplate.delete(key) }
    }

    override fun increment(key: String): Long =
        runCatching { masterRedisTemplate.opsForValue().increment(key) ?: 0L }.getOrDefault(0L)
}
