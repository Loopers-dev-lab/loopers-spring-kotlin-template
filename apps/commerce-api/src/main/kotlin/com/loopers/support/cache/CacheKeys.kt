package com.loopers.support.cache

import org.springframework.data.domain.Pageable
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

    data class Ranking(
        private val date: LocalDateTime,
    ) : CacheKeys(ttl = Duration.ofDays(2)) {
        override val key: String = buildKey("ranking-v1:${date.toLocalDate().format(DATE_FORMATTER)}")

        companion object {
            private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
        }
    }

    companion object {
        private const val PREFIX = "LOOPERS"
        private fun buildKey(key: Any) = "$PREFIX::$key"
    }
}
