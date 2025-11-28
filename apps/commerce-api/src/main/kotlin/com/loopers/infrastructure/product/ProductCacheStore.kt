package com.loopers.infrastructure.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.ProductInfo
import com.loopers.domain.product.ProductSortType
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ProductCacheStore(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PRODUCT_CACHE_PREFIX = "product:"
        private const val PRODUCT_LIST_CACHE_PREFIX = "products:"
        private const val PRODUCT_TTL_SECONDS = 600L // 10분
        private const val PRODUCT_LIST_TTL_SECONDS = 300L // 5분
    }

    fun getProduct(id: Long): ProductInfo? {
        return try {
            val key = "$PRODUCT_CACHE_PREFIX$id"
            val cached = redisTemplate.opsForValue().get(key) ?: return null
            objectMapper.readValue(cached, ProductInfo::class.java)
        } catch (e: Exception) {
            logger.warn("상품 캐시 조회 실패: id=$id", e)
            null
        }
    }

    fun setProduct(id: Long, product: ProductInfo) {
        try {
            val key = "$PRODUCT_CACHE_PREFIX$id"
            val json = objectMapper.writeValueAsString(product)
            redisTemplate.opsForValue().set(key, json, PRODUCT_TTL_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.warn("상품 캐시 저장 실패: id=$id", e)
        }
    }

    fun getProductList(brandId: Long?, sort: ProductSortType, page: Int): CachedPage? {
        return try {
            val key = buildListCacheKey(brandId, sort, page)
            val cached = redisTemplate.opsForValue().get(key) ?: return null
            objectMapper.readValue(cached, CachedPage::class.java)
        } catch (e: Exception) {
            logger.warn("상품 목록 캐시 조회 실패: brandId=$brandId, sort=$sort, page=$page", e)
            null
        }
    }

    fun setProductList(brandId: Long?, sort: ProductSortType, page: Int, products: List<ProductInfo>, totalElements: Long) {
        try {
            val key = buildListCacheKey(brandId, sort, page)
            val cacheData = CachedPage(products, totalElements)
            val json = objectMapper.writeValueAsString(cacheData)
            redisTemplate.opsForValue().set(key, json, PRODUCT_LIST_TTL_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.warn("상품 목록 캐시 저장 실패: brandId=$brandId, sort=$sort, page=$page", e)
        }
    }

    fun evictProduct(id: Long) {
        try {
            redisTemplate.delete("$PRODUCT_CACHE_PREFIX$id")
        } catch (e: Exception) {
            logger.warn("상품 캐시 삭제 실패: id=$id", e)
        }
    }

    fun evictProductLists() {
        try {
            val pattern = "$PRODUCT_LIST_CACHE_PREFIX*"
            val keys = redisTemplate.keys(pattern)
            if (keys.isNotEmpty()) {
                redisTemplate.delete(keys)
            }
        } catch (e: Exception) {
            logger.warn("상품 목록 캐시 삭제 실패", e)
        }
    }

    private fun buildListCacheKey(brandId: Long?, sort: ProductSortType, page: Int): String {
        val brand = brandId ?: "all"
        return "${PRODUCT_LIST_CACHE_PREFIX}brand:${brand}:sort:${sort}:page:${page}"
    }

    data class CachedPage(
        val content: List<ProductInfo>,
        val totalElements: Long
    )
}
