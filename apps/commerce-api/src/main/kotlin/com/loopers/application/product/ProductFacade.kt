package com.loopers.application.product

import com.loopers.application.ranking.RankingFacade
import com.loopers.application.ranking.RankingInfo
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductSortType
import com.loopers.infrastructure.product.ProductCacheStore
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val productService: ProductService,
    private val productCacheStore: ProductCacheStore,
    private val rankingFacade: RankingFacade,
) {

    /**
     * 상품 상세 조회 (랭킹 정보 포함)
     *
     * @param id 상품 ID
     * @param includeRanking 랭킹 정보 포함 여부 (기본값: true)
     * @return 상품 정보 + 랭킹 정보
     */
    fun getProductWithRanking(id: Long, includeRanking: Boolean = true): ProductInfoWithRanking{
        val product = getProduct(id) // 기존 메서드 재사용 (캐시 적용됨)

        if (!includeRanking) {
            return ProductInfoWithRanking(product, null, null)
        }

        // 랭킹 정보 조회 (순위권 밖이면 null)
        val rankingInfo = rankingFacade.getProductRank(id, null)
        return ProductInfoWithRanking(product, rankingInfo?.rank, rankingInfo?.score)
    }

    fun getProduct(productId: Long): ProductInfo {
        // 1. 캐시 조회
        val cached = productCacheStore.getProduct(productId)
        if (cached != null) return cached

        // 2. DB 조회
        val product = productService.getProduct(productId)
        val productInfo = ProductInfo.from(product)

        // 3. 캐시 저장
        productCacheStore.setProduct(productId, productInfo)
        return productInfo
    }

    /**
     * 여러 상품 정보 일괄 조회
     * @param productIds 상품 ID 리스트
     * @return 상품 정보 리스트 (없는 ID는 제외됨)
     */
    @Transactional(readOnly = true)
    fun getProductsByIds(productIds: List<Long>): List<ProductInfo> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return productService.getProductsByIds(productIds)
            .map { ProductInfo.from(it) }
    }

    fun getProducts(brandId: Long?, sort: ProductSortType, pageable: Pageable): Page<ProductInfo> {
        val cachedPage = productCacheStore.getProductList(brandId, sort, pageable.pageNumber)
        if (cachedPage != null) {
            return PageImpl(cachedPage.content, pageable, cachedPage.totalElements)
        }

        val products = productService.getProducts(brandId, sort, pageable)
        val productInfoPage = ProductInfo.fromPage(products)

        productCacheStore.setProductList(
            brandId,
            sort,
            pageable.pageNumber,
            productInfoPage.content,
            productInfoPage.totalElements
        )

        return productInfoPage
    }

}

/**
 * 랭킹 정보가 포함된 상품 정보
 *
 */
data class ProductInfoWithRanking(
    val product: ProductInfo,
    val rank: Long?,
    val score: Double?
)
