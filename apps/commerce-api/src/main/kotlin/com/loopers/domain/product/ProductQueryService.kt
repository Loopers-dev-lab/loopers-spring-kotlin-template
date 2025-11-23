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

        // 1. Redis에서 먼저 조회 (예외 처리로 fallback 보장)
        try {
            val cached = redisTemplate.opsForValue().get(cacheKey)
            if (cached != null) {
                val products: Page<Product> = objectMapper.readValue(cached)
                // 캐시된 데이터라도 최신 좋아요 수를 Redis에서 가져와서 반영
                products.content.forEach { product ->
                    val likeCount = productLikeCountService.getLikeCount(product.id)
                    product.setLikeCount(likeCount)
                }
                return products
            }
        } catch (e: Exception) {
            logger.error("Failed to read product list from Redis cache, falling back to DB: cacheKey=$cacheKey", e)
            // 캐시 실패 시 DB로 fallback
        }

        // 2. DB 조회
        val products = productRepository.findAll(brandId, sort, pageable)

        // 3. 최신 좋아요 수를 Redis에서 가져와서 반영
        products.content.forEach { product ->
            val likeCount = productLikeCountService.getLikeCount(product.id)
            product.setLikeCount(likeCount)
        }

        // 4. Redis에 캐시 저장 (5분 TTL, 실패해도 응답에 영향 없음)
        try {
            val cacheValue = objectMapper.writeValueAsString(products)
            redisTemplate.opsForValue().set(cacheKey, cacheValue, PRODUCT_LIST_TTL)
        } catch (e: Exception) {
            logger.error("Failed to write product list to Redis cache: cacheKey=$cacheKey", e)
            // 캐시 쓰기 실패는 무시하고 계속 진행
        }

        return products
    }

    private fun buildProductListCacheKey(brandId: Long?, sort: String, pageable: Pageable): String {
        val brand = brandId ?: "all"
        return "${PRODUCT_LIST_CACHE_PREFIX}brand:$brand:sort:$sort:page:${pageable.pageNumber}:size:${pageable.pageSize}"
    }

    fun getProductDetail(productId: Long): ProductDetailData {
        val cacheKey = "$PRODUCT_DETAIL_CACHE_PREFIX$productId"

        // 1. Redis에서 먼저 조회 (예외 처리로 fallback 보장)
        try {
            val cached = redisTemplate.opsForValue().get(cacheKey)
            if (cached != null) {
                val productDetailData: ProductDetailData = objectMapper.readValue(cached)
                // 캐시된 데이터라도 최신 좋아요 수를 Redis에서 가져와서 반영
                val likeCount = productLikeCountService.getLikeCount(productId)
                productDetailData.product.setLikeCount(likeCount)
                return productDetailData
            }
        } catch (e: Exception) {
            logger.error("Failed to read product detail from Redis cache, falling back to DB: productId=$productId", e)
            // 캐시 실패 시 DB로 fallback
        }

        // 2. DB 조회
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다: $productId")

        val stock = stockRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다: $productId")

        // 3. 최신 좋아요 수를 Redis에서 가져와서 반영
        val likeCount = productLikeCountService.getLikeCount(productId)
        product.setLikeCount(likeCount)

        val productDetailData = ProductDetailData(product, stock)

        // 4. Redis에 캐시 저장 (10분 TTL, 실패해도 응답에 영향 없음)
        try {
            val cacheValue = objectMapper.writeValueAsString(productDetailData)
            redisTemplate.opsForValue().set(cacheKey, cacheValue, PRODUCT_DETAIL_TTL)
        } catch (e: Exception) {
            logger.error("Failed to write product detail to Redis cache: productId=$productId", e)
            // 캐시 쓰기 실패는 무시하고 계속 진행
        }

        return productDetailData
    }

    fun getProductsByIds(productIds: List<Long>): List<Product> {
        return productRepository.findByIdInAndDeletedAtIsNull(productIds)
    }
}
