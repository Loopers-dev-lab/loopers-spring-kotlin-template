package com.loopers.support.cache

import java.time.Duration

interface CachePolicy {
    val namespace: String
    fun getTtl(kind: String): Duration?
}
