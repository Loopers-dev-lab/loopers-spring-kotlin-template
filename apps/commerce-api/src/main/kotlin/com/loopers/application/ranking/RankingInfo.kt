package com.loopers.application.ranking

import com.loopers.domain.common.vo.Money

class RankingInfo {

    data class CacheRanking(
        val rank: Int,
        val productId: Long,
        val likeCount: Long,
        val viewCount: Long,
        val orderCount: Long,
    )

    data class RankingItem(
        val rank: Int,
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val price: Money,
        val likeCount: Long,
        val viewCount: Long,
        val orderCount: Long,
    ) {
        companion object {
            fun from(
                cacheRanking: CacheRanking,
                productWithBrand: ProductWithBrand,
            ): RankingItem = RankingItem(
                rank = cacheRanking.rank,
                productId = productWithBrand.product.id,
                productName = productWithBrand.product.name,
                brandId = productWithBrand.brand.id,
                brandName = productWithBrand.brand.name,
                price = productWithBrand.product.price,
                likeCount = cacheRanking.likeCount,
                viewCount = cacheRanking.viewCount,
                orderCount = cacheRanking.orderCount,
            )
        }
    }

    data class Page(
        val items: List<RankingItem>,
        val pageNumber: Int,
        val pageSize: Int,
        val totalElements: Long,
    )
}
