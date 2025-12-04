package com.loopers.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisCacheTemplate(
    private val objectMapper: ObjectMapper,
    private val redisTemplate: RedisTemplate<String, String>,
) : CacheTemplate {
    private val valueOps = redisTemplate.opsForValue()

    @Suppress("kotlin:S6518") // operator function 이 아님
    override fun <T> get(cacheKey: CacheKey, typeReference: TypeReference<T>): T? {
        return runCatching {
            val value = valueOps.get(cacheKey.key) ?: return null
            objectMapper.readValue(value, typeReference)
        }.getOrNull()
    }

    override fun <T, KEY : CacheKey> getAll(
        cacheKeys: List<KEY>,
        typeReference: TypeReference<T>,
    ): List<T> {
        if (cacheKeys.isEmpty()) return emptyList()

        return runCatching {
            val values: List<String?> = valueOps.multiGet(cacheKeys.map { it.key }) ?: emptyList()

            values.mapNotNull { value ->
                value ?: return@mapNotNull null

                runCatching {
                    objectMapper.readValue(value, typeReference)
                }.getOrNull()
            }
        }.getOrElse { emptyList() }
    }

    @Suppress("kotlin:S6518") // operator function 이 아님
    override fun <T> put(cacheKey: CacheKey, value: T) {
        runCatching {
            valueOps.set(cacheKey.key, objectMapper.writeValueAsString(value), cacheKey.ttl)
        }
    }

    @Suppress("kotlin:S6518") // operator function 이 아님
    override fun <T, KEY : CacheKey> putAll(cacheMap: Map<KEY, T>) {
        if (cacheMap.isEmpty()) return

        runCatching {
            cacheMap.forEach { (cacheKey, value) ->
                valueOps.set(cacheKey.key, objectMapper.writeValueAsString(value), cacheKey.ttl)
            }
        }
    }

    override fun evict(cacheKey: CacheKey) {
        runCatching {
            redisTemplate.delete(cacheKey.key)
        }
    }

    override fun evictAll(cacheKeys: List<CacheKey>) {
        if (cacheKeys.isEmpty()) return

        runCatching {
            redisTemplate.delete(cacheKeys.map { it.key })
        }
    }

    override fun <T> cacheAside(
        cacheKey: CacheKey,
        typeReference: TypeReference<T>,
        block: () -> T,
    ): T {
        val cachedData = get(cacheKey, typeReference)

        if (cachedData != null) return cachedData

        val result = block()
        put(cacheKey, result)
        return result
    }
}
