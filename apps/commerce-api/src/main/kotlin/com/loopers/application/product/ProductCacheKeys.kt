package com.loopers.application.product

import com.loopers.cache.CacheKey
import com.loopers.domain.product.ProductSortType
import java.time.Duration

sealed class ProductCacheKeys(override val ttl: Duration) : CacheKey {
    abstract override val key: String
    abstract override val traceKey: String

    /**
     * 상품 목록 조회 캐시
     *
     * 키 패턴: product-list:v1:{sort}:{filter}:{page}:{size}
     * TTL: 60초
     * 캐싱 조건: page 0, 1, 2만 (3페이지 이내)
     */
    data class ProductList(
        private val sort: ProductSortType?,
        private val brandId: Long?,
        private val page: Int?,
        private val size: Int?,
    ) : ProductCacheKeys(ttl = Duration.ofSeconds(60)) {
        override val key: String =
            "product-list:v1:${sort?.name ?: "_"}:${brandId?.let { "brand_$it" } ?: "_"}:${page ?: 0}:${size ?: 20}"
        override val traceKey: String = "product-list"

        fun shouldCache(): Boolean = (page ?: 0) <= 2
    }

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
