package com.loopers.application.product

import com.loopers.cache.CacheKey
import java.time.Duration

sealed class ProductCacheKeys(override val ttl: Duration) : CacheKey {
    abstract override val key: String
    abstract override val traceKey: String

    /**
     * 상품 상세 조회 캐시
     *
     * 키 패턴: product-detail:v1:{productId}
     * TTL: 60초
     */
    data class ProductDetail(
        private val productId: Long,
    ) : ProductCacheKeys(ttl = Duration.ofSeconds(60)) {
        override val key: String = "product-detail:v1:$productId"
        override val traceKey: String = "product-detail"
    }
}
