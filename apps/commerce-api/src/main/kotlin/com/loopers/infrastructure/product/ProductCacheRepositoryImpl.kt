package com.loopers.infrastructure.product

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.product.ProductCacheRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ProductCacheRepositoryImpl(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : ProductCacheRepository {
    companion object {
        private const val PRODUCT_DETAIL_CACHE_PREFIX = "product:detail:"
        private const val PRODUCT_LIST_CACHE_PREFIX = "product:list:"
        private val logger = LoggerFactory.getLogger(ProductCacheRepositoryImpl::class.java)
    }

    override fun <T> get(cacheKey: String, typeReference: TypeReference<T>): T? =
        runCatching {
            redisTemplate.opsForValue().get(cacheKey)?.let { cached ->
                objectMapper.readValue(cached, typeReference)
            }
        }.onFailure { e ->
            logger.error("Failed to read from Redis cache: cacheKey=$cacheKey", e)
        }.getOrNull()

    override fun <T> set(
        cacheKey: String,
        data: T,
        ttl: Duration,
    ) {
        runCatching {
            val cacheValue = objectMapper.writeValueAsString(data)
            redisTemplate.opsForValue().set(cacheKey, cacheValue, ttl)
        }.onFailure { e ->
            logger.error("Failed to write to Redis cache: cacheKey=$cacheKey", e)
        }
    }

    override fun delete(cacheKey: String) {
        redisTemplate.delete(cacheKey)
    }

    override fun deleteByPattern(pattern: String) {
        val keys = scanKeys(pattern)
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
        }
    }

    override fun buildProductDetailCacheKey(productId: Long): String =
        "$PRODUCT_DETAIL_CACHE_PREFIX$productId"

    override fun buildProductListCacheKey(
        brandId: Long?,
        sort: String,
        pageNumber: Int,
        pageSize: Int,
    ): String {
        val brand = brandId ?: "all"
        return "${PRODUCT_LIST_CACHE_PREFIX}brand:$brand:sort:$sort:page:$pageNumber:size:$pageSize"
    }

    override fun getProductListCachePattern(): String = "$PRODUCT_LIST_CACHE_PREFIX*"

    private fun scanKeys(pattern: String): Set<String> {
        val keys = mutableSetOf<String>()

        redisTemplate.execute { connection ->
            val scanOptions = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build()

            connection.scan(scanOptions).use { cursor ->
                while (cursor.hasNext()) {
                    keys.add(String(cursor.next()))
                }
            }
        }

        return keys
    }
}
