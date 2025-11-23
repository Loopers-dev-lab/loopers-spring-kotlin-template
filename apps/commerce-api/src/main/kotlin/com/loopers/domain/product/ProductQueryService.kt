package com.loopers.domain.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
    }

    fun findProducts(brandId: Long?, sort: String, pageable: Pageable): Page<Product> {
        val cacheKey = buildProductListCacheKey(brandId, sort, pageable)

        // 1. Redis에서 먼저 조회
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

        // 2. DB 조회
        val products = productRepository.findAll(brandId, sort, pageable)

        // 3. 최신 좋아요 수를 Redis에서 가져와서 반영
        products.content.forEach { product ->
            val likeCount = productLikeCountService.getLikeCount(product.id)
            product.setLikeCount(likeCount)
        }

        // 4. Redis에 캐시 저장 (5분 TTL)
        val cacheValue = objectMapper.writeValueAsString(products)
        redisTemplate.opsForValue().set(cacheKey, cacheValue, PRODUCT_LIST_TTL)

        return products
    }

    private fun buildProductListCacheKey(brandId: Long?, sort: String, pageable: Pageable): String {
        val brand = brandId ?: "all"
        return "${PRODUCT_LIST_CACHE_PREFIX}brand:$brand:sort:$sort:page:${pageable.pageNumber}:size:${pageable.pageSize}"
    }

    fun getProductDetail(productId: Long): ProductDetailData {
        val cacheKey = "$PRODUCT_DETAIL_CACHE_PREFIX$productId"

        // 1. Redis에서 먼저 조회
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            val productDetailData: ProductDetailData = objectMapper.readValue(cached)
            // 캐시된 데이터라도 최신 좋아요 수를 Redis에서 가져와서 반영
            val likeCount = productLikeCountService.getLikeCount(productId)
            productDetailData.product.setLikeCount(likeCount)
            return productDetailData
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

        // 4. Redis에 캐시 저장 (10분 TTL)
        val cacheValue = objectMapper.writeValueAsString(productDetailData)
        redisTemplate.opsForValue().set(cacheKey, cacheValue, PRODUCT_DETAIL_TTL)

        return productDetailData
    }

    fun getProductsByIds(productIds: List<Long>): List<Product> {
        return productRepository.findByIdInAndDeletedAtIsNull(productIds)
    }
}
