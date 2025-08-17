package com.loopers.domain.product.cache

import com.loopers.domain.product.dto.criteria.ProductCriteria
import com.loopers.domain.product.policy.ProductCachePolicy
import com.loopers.support.cache.CacheKeyDsl

object ProductCacheKey {
    val listNamespace = ProductCachePolicy.namespace + ":list"
    val listCountNamespace = ProductCachePolicy.namespace + ":list:count"

    // 페이지 목록 캐시 키
    fun listKey(criteria: ProductCriteria.FindAll): String =
        CacheKeyDsl(listNamespace)
            .part("brandId", criteria.brandIds?.takeIf { it.isNotEmpty() })
            .part("sort", criteria.sort.name)
            .part("page", criteria.page)
            .part("size", criteria.size)
            .build()

    // 총 카운트 캐시 키
    fun countKey(criteria: ProductCriteria.FindAll): String =
        CacheKeyDsl(listCountNamespace)
            .part("brandId", criteria.brandIds?.takeIf { it.isNotEmpty() })
            .build()
}
