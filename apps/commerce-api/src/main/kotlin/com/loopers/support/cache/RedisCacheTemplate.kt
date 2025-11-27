package com.loopers.support.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
internal class RedisCacheTemplate(
    private val objectMapper: ObjectMapper,
    private val redisTemplate: RedisTemplate<String, String>,
) : CacheTemplate {

    private val valueOps = redisTemplate.opsForValue()

    override fun <T> put(cacheKey: CacheKey, value: T) {
        valueOps.set(cacheKey.key, objectMapper.writeValueAsString(value), cacheKey.ttl)
    }

    override fun <T> get(cacheKey: CacheKey, typeReference: TypeReference<T>): T? = runCatching {
        objectMapper.readValue<T>(valueOps.get(cacheKey.key), typeReference)
    }.getOrNull()

    override fun <T> cacheAside(
        cacheKey: CacheKey,
        typeReference: TypeReference<T>,
        block: () -> T,
    ): T {
        val data = get(cacheKey, typeReference)

        if (data != null) return data

        val result = block()
        put(cacheKey, result)
        return result
    }
}
