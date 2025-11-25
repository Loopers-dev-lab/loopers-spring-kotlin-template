package com.loopers.infrastructure.product

import com.loopers.application.dto.PageResult
import com.loopers.application.product.ProductCache
import com.loopers.application.product.ProductResult
import com.loopers.domain.product.ProductSort
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ProductCacheImpl(
    private val productListRedisTemplate: RedisTemplate<String, PageResult<ProductResult.ListInfo>>,
    private val productDetailRedisTemplate: RedisTemplate<String, ProductResult.DetailInfo>,
    private val productLikedListRedisTemplate: RedisTemplate<String, PageResult<ProductResult.LikedInfo>>,
) : ProductCache {

    private val log = LoggerFactory.getLogger(ProductCacheImpl::class.java)

    companion object {
        private const val CACHE_DETAIL_TTL_MINUTE = 5L
        private const val CACHE_LIST_TTL_MINUTE = 5L
        private const val CACHE_LIKE_LIST_TTL_MINUTE = 5L
        private const val VERSION = 1L
        private const val MAX_CACHEABLE_PAGE = 4
    }

    override fun getProductDetail(productId: Long, userId: String?): ProductResult.DetailInfo? {
        val key = generateDetailKey(productId, userId)
        return safeGet(key, productDetailRedisTemplate)
    }

    override fun setProductDetail(productId: Long, userId: String?, value: ProductResult.DetailInfo) {
        val key = generateDetailKey(productId, userId)
        productDetailRedisTemplate.opsForValue().set(key, value, CACHE_DETAIL_TTL_MINUTE, TimeUnit.MINUTES)
    }

    override fun getProductList(brandId: Long?, sort: ProductSort, pageable: Pageable): PageResult<ProductResult.ListInfo>? {
        val key = generateListKey(brandId, sort, pageable)
        return safeGet(key, productListRedisTemplate)
    }

    override fun setProductList(
        brandId: Long?,
        sort: ProductSort,
        pageable: Pageable,
        value: PageResult<ProductResult.ListInfo>,
    ) {
        if (pageable.pageNumber > MAX_CACHEABLE_PAGE) {
            return
        }
        val key = generateListKey(brandId, sort, pageable)
        productListRedisTemplate.opsForValue().set(key, value, CACHE_LIST_TTL_MINUTE, TimeUnit.MINUTES)
    }

    override fun getLikedProductList(userId: String, pageable: Pageable): PageResult<ProductResult.LikedInfo>? {
        val key = generateLikedListKey(userId, pageable)
        return safeGet(key, productLikedListRedisTemplate)
    }

    override fun setLikedProductList(userId: String, pageable: Pageable, value: PageResult<ProductResult.LikedInfo>) {
        if (pageable.pageNumber > MAX_CACHEABLE_PAGE) {
            return
        }
        val key = generateLikedListKey(userId, pageable)
        productLikedListRedisTemplate.opsForValue().set(key, value, CACHE_LIKE_LIST_TTL_MINUTE, TimeUnit.MINUTES)
    }

    override fun evictProductDetail(productId: Long) {
        val pattern = "product:$VERSION:detail:$productId:*"
        val keys = productDetailRedisTemplate.keys(pattern)
        if (!keys.isNullOrEmpty()) {
            productDetailRedisTemplate.delete(keys)
        }
    }

    override fun evictProductList() {
        val pattern = "product:$VERSION:list:*"
        val keys = productListRedisTemplate.keys(pattern)
        if (!keys.isNullOrEmpty()) {
            productListRedisTemplate.delete(keys)
        }
    }

    override fun evictLikedProductList(userId: String) {
        val pattern = "product:$VERSION:liked:$userId:*"
        val keys = productLikedListRedisTemplate.keys(pattern)
        if (!keys.isNullOrEmpty()) {
            productLikedListRedisTemplate.delete(keys)
        }
    }

    private fun <T> safeGet(key: String, template: RedisTemplate<String, T>): T? {
        return try {
            template.opsForValue().get(key)
        } catch (e: Exception) {
            log.warn("캐시 역직렬화 실패, 캐시 삭제: key=$key", e)
            template.delete(key)
            null
        }
    }

    private fun generateListKey(brandId: Long?, sort: ProductSort, pageable: Pageable): String {
        return "product:$VERSION:list:${brandId ?: "all"}:${sort.name}:${pageable.pageNumber}:${pageable.pageSize}"
    }

    private fun generateDetailKey(productId: Long, userId: String?): String {
        return "product:$VERSION:detail:$productId:${userId ?: "anonymous"}"
    }

    private fun generateLikedListKey(userId: String, pageable: Pageable): String {
        return "product:$VERSION:liked:$userId:${pageable.pageNumber}:${pageable.pageSize}"
    }
}
