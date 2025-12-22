package com.loopers.support.cache

import org.springframework.data.domain.Pageable
import java.time.Duration

sealed class CacheKeys(override val ttl: Duration) : CacheKey {
    abstract override val key: String

    data class ProductDetail(private val productId: Long) : CacheKeys(ttl = Duration.ofMinutes(1)) {
        override val key: String = buildKey("product-detail-v1:$productId")
    }

    data class ProductViewModelPage(
        private val pageable: Pageable,
        private val brandId: Long?,
    ) : CacheKeys(ttl = Duration.ofMinutes(1)) {
        override val key: String = buildKey(
            "product-view-model-page-v1:page=${pageable.pageNumber}:size=${pageable.pageSize}:sort=${pageable.sort}:brandId=${brandId}",
        )
    }

    companion object {
        private const val PREFIX = "LOOPERS"
        private fun buildKey(key: Any) = "$PREFIX::$key"
    }
}
