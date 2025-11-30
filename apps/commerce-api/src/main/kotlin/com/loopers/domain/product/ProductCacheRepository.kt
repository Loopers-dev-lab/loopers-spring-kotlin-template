package com.loopers.domain.product

import com.fasterxml.jackson.core.type.TypeReference
import java.time.Duration

interface ProductCacheRepository {
    fun <T> get(cacheKey: String, typeReference: TypeReference<T>): CacheResult<T>
    fun <T> set(cacheKey: String, data: T, ttl: Duration)
    fun delete(cacheKey: String)
    fun deleteByPattern(pattern: String)
    fun buildProductDetailCacheKey(productId: Long): String
    fun buildProductListCacheKey(brandId: Long?, sort: SortType, pageNumber: Int, pageSize: Int): String
    fun getProductListCachePattern(): String
}
