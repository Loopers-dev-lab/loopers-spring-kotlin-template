package com.loopers.support.cache

import java.time.Duration

sealed class CacheKeys(override val ttl: Duration) : CacheKey {
    abstract override val key: String

    data class ProductDetail(private val productId: Long) : CacheKeys(ttl = Duration.ofMinutes(1)) {
        override val key: String = buildKey("product-detail-v1:$productId")
    }

    companion object {
        private const val PREFIX = "LOOPERS"
        private fun buildKey(key: Any) = "$PREFIX::$key"
    }
}
