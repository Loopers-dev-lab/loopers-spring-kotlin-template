package com.loopers.support.cache

import java.time.Duration

interface CacheKey {
    val key: String
    val ttl: Duration
}
