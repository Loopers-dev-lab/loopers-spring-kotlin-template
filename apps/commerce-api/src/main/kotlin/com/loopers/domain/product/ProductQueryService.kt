package com.loopers.domain.product

import com.fasterxml.jackson.core.type.TypeReference
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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
    private val productCacheRepository: ProductCacheRepository,
) {
    companion object {
        private val PRODUCT_DETAIL_TTL = Duration.ofMinutes(10)
        private val PRODUCT_LIST_TTL = Duration.ofMinutes(5)
    }

    fun findProducts(brandId: Long?, sort: String, pageable: Pageable): Page<Product> {
        val cacheKey = productCacheRepository.buildProductListCacheKey(
            brandId,
            sort,
            pageable.pageNumber,
            pageable.pageSize,
        )

        return getWithCache(
            cacheKey = cacheKey,
            ttl = PRODUCT_LIST_TTL,
            typeReference = object : TypeReference<Page<Product>>() {},
            fetchFromDb = { fetchProductsFromDatabase(brandId, sort, pageable) },
            syncLikeCounts = { products ->
                syncLikeCountsForProducts(products.content)
                products
            },
        )
    }

    fun getProductDetail(productId: Long): ProductDetailData {
        val cacheKey = productCacheRepository.buildProductDetailCacheKey(productId)

        return getWithCache(
            cacheKey = cacheKey,
            ttl = PRODUCT_DETAIL_TTL,
            typeReference = object : TypeReference<ProductDetailData>() {},
            fetchFromDb = { fetchProductDetailFromDatabase(productId) },
            syncLikeCounts = { detail ->
                syncLikeCountForProduct(detail.product)
                detail
            },
        )
    }

    fun getProductsByIds(productIds: List<Long>): List<Product> =
        productRepository.findByIdInAndDeletedAtIsNull(productIds)

    private fun <T> getWithCache(
        cacheKey: String,
        ttl: Duration,
        typeReference: TypeReference<T>,
        fetchFromDb: () -> T,
        syncLikeCounts: (T) -> T,
    ): T {
        val cachedData = productCacheRepository.get(cacheKey, typeReference)
        if (cachedData != null) {
            return syncLikeCounts(cachedData)
        }

        val freshData = fetchFromDb()
        val dataWithLikeCounts = syncLikeCounts(freshData)
        productCacheRepository.set(cacheKey, dataWithLikeCounts, ttl)

        return dataWithLikeCounts
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
}
