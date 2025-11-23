package com.loopers.domain.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

data class ProductDetailData(
    val product: Product,
    val stock: Stock,
)

@Service
class ProductQueryService(
    private val productRepository: ProductRepository,
    private val stockRepository: StockRepository,
    private val productLikeCountService: ProductLikeCountService,
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val PRODUCT_DETAIL_CACHE_PREFIX = "product:detail:"
        private const val PRODUCT_LIST_CACHE_PREFIX = "product:list:"
        private val PRODUCT_DETAIL_TTL = Duration.ofMinutes(10)
        private val PRODUCT_LIST_TTL = Duration.ofMinutes(5)
        private val logger = LoggerFactory.getLogger(ProductQueryService::class.java)
    }

    fun findProducts(brandId: Long?, sort: String, pageable: Pageable): Page<Product> {
        val cacheKey = buildProductListCacheKey(brandId, sort, pageable)

        return getWithCache(
            cacheKey = cacheKey,
            ttl = PRODUCT_LIST_TTL,
            fetchFromDb = { fetchProductsFromDatabase(brandId, sort, pageable) },
            syncLikeCounts = { products ->
                syncLikeCountsForProducts(products.content)
                products
            },
        )
    }

    fun getProductDetail(productId: Long): ProductDetailData {
        val cacheKey = "$PRODUCT_DETAIL_CACHE_PREFIX$productId"

        return getWithCache(
            cacheKey = cacheKey,
            ttl = PRODUCT_DETAIL_TTL,
            fetchFromDb = { fetchProductDetailFromDatabase(productId) },
            syncLikeCounts = { detail ->
                syncLikeCountForProduct(detail.product)
                detail
            },
        )
    }

    fun getProductsByIds(productIds: List<Long>): List<Product> =
        productRepository.findByIdInAndDeletedAtIsNull(productIds)

    private inline fun <reified T> getWithCache(
        cacheKey: String,
        ttl: Duration,
        fetchFromDb: () -> T,
        syncLikeCounts: (T) -> T,
    ): T {
        val cachedData = getFromCache<T>(cacheKey)
        if (cachedData != null) {
            return syncLikeCounts(cachedData)
        }

        val freshData = fetchFromDb()
        val dataWithLikeCounts = syncLikeCounts(freshData)
        saveToCache(cacheKey, dataWithLikeCounts, ttl)

        return dataWithLikeCounts
    }

    private inline fun <reified T> getFromCache(cacheKey: String): T? =
        runCatching {
            redisTemplate.opsForValue().get(cacheKey)?.let { cached ->
                objectMapper.readValue<T>(cached)
            }
        }.onFailure { e ->
            logger.error("Failed to read from Redis cache: cacheKey=$cacheKey", e)
        }.getOrNull()

    private fun <T> saveToCache(cacheKey: String, data: T, ttl: Duration) {
        runCatching {
            val cacheValue = objectMapper.writeValueAsString(data)
            redisTemplate.opsForValue().set(cacheKey, cacheValue, ttl)
        }.onFailure { e ->
            logger.error("Failed to write to Redis cache: cacheKey=$cacheKey", e)
        }
    }

    private fun fetchProductsFromDatabase(brandId: Long?, sort: String, pageable: Pageable): Page<Product> =
        productRepository.findAll(brandId, sort, pageable)

    private fun fetchProductDetailFromDatabase(productId: Long): ProductDetailData {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        val stock = stockRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: $productId")

        return ProductDetailData(product, stock)
    }

    private fun syncLikeCountsForProducts(products: List<Product>) {
        products.forEach { product ->
            syncLikeCountForProduct(product)
        }
    }

    private fun syncLikeCountForProduct(product: Product) {
        val likeCount = productLikeCountService.getLikeCount(product.id)
        product.setLikeCount(likeCount)
    }

    private fun buildProductListCacheKey(brandId: Long?, sort: String, pageable: Pageable): String {
        val brand = brandId ?: "all"
        return "${PRODUCT_LIST_CACHE_PREFIX}brand:$brand:sort:$sort:page:${pageable.pageNumber}:size:${pageable.pageSize}"
    }
}
