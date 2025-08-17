package com.loopers.support.cache

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class GenericCacheRepository(
    private val cacheStore: CacheStore,
    private val objectMapper: ObjectMapper,
) {
    fun <T> cacheAside(
        kind: String,
        policy: CachePolicy,
        key: String,
        typeRef: TypeReference<T>,
        loader: () -> T,
    ): T {
        cacheStore.get(key)
            ?.let {
                return objectMapper.readValue(it, typeRef)
            }

        val loaded = loader()

        val ttl = policy.getTtl(kind)
        val json = objectMapper.writeValueAsString(loaded)
        if (ttl != null) cacheStore.set(key, json, ttl) else cacheStore.set(key, json)

        return loaded
    }
}
