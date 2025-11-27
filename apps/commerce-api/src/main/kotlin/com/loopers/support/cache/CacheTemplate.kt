package com.loopers.support.cache

import com.fasterxml.jackson.core.type.TypeReference

interface CacheTemplate {
    fun <T> get(cacheKey: CacheKey, typeReference: TypeReference<T>): T?

    fun <T> put(cacheKey: CacheKey, value: T)

    fun <T> cacheAside(cacheKey: CacheKey, typeReference: TypeReference<T>, block: () -> T): T
}
