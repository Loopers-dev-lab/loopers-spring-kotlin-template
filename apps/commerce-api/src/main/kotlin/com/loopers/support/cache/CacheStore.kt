package com.loopers.support.cache

import java.time.Duration

interface CacheStore {
    fun get(key: String): String?
    fun getMaster(key: String): String?
    fun set(key: String, value: String)
    fun set(key: String, value: String, ttl: Duration)
    fun delete(key: String)
    fun increment(key: String): Long
}
