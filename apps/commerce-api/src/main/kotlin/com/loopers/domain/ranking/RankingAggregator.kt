package com.loopers.domain.ranking

import com.loopers.application.ranking.dto.RankingResult
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component

/**
 * 랭킹 데이터와 상품/브랜드 정보를 집계하여 랭킹 결과를 생성하는 컴포넌트
 */
@Component
class RankingAggregator(
    private val productService: ProductService,
    private val brandService: BrandService,
) {

    /**
     * 랭킹 점수 목록을 상품/브랜드 정보와 결합하여 랭킹 결과 목록을 생성
     *
     * @param pageScores 페이지 범위의 랭킹 점수 목록
     * @param startRank 시작 순위 (1-based)
     * @return 랭킹 결과 목록
     */
    fun aggregate(pageScores: List<RankingScore>, startRank: Long): List<RankingResult.RankedInfo> {
        if (pageScores.isEmpty()) {
            return emptyList()
        }

        val productIds = pageScores.map { it.productId }
        val products = productService.getProducts(productIds)

        if (products.isEmpty()) {
            return emptyList()
        }

        val productMap = products.associateBy { it.id }
        val brandMap = fetchBrandMap(products)

        return pageScores.mapIndexedNotNull { index, scoreEntry ->
            createRankingItem(
                scoreEntry = scoreEntry,
                productMap = productMap,
                brandMap = brandMap,
                rank = startRank + index,
            )
        }
    }

    private fun fetchBrandMap(products: List<Product>): Map<Long, Brand> {
        val brandIds = products.map { it.brandId }.distinct()
        return brandService.getAllBrand(brandIds).associateBy { it.id }
    }

    private fun createRankingItem(
        scoreEntry: RankingScore,
        productMap: Map<Long, Product>,
        brandMap: Map<Long, Brand>,
        rank: Long,
    ): RankingResult.RankedInfo? {
        val product = productMap[scoreEntry.productId] ?: return null
        val brand = brandMap[product.brandId] ?: return null

        return RankingResult.RankedInfo(
            productId = product.id,
            name = product.name,
            price = product.price,
            brandName = brand.name,
            score = scoreEntry.score,
            rank = rank,
        )
    }
}
