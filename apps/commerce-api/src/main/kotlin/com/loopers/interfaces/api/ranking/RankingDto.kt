package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingInfo
import com.loopers.domain.common.vo.Money

sealed class RankingDto {

    data class RankingItemResponse(
        val rank: Int,
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val brandName: String,
        val price: Money,
    ) {
        companion object {
            fun from(item: RankingInfo.RankingItem): RankingItemResponse = RankingItemResponse(
                rank = item.rank,
                productId = item.productId,
                productName = item.productName,
                brandId = item.brandId,
                brandName = item.brandName,
                price = item.price,
            )
        }
    }

    data class PageResponse(
        val content: List<RankingItemResponse>,
        val pageNumber: Int,
        val pageSize: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(rankingInfo: RankingInfo.Page): PageResponse = PageResponse(
                content = rankingInfo.items.map { RankingItemResponse.from(it) },
                pageNumber = rankingInfo.pageNumber,
                pageSize = rankingInfo.pageSize,
                totalElements = rankingInfo.totalElements,
                totalPages = ((rankingInfo.totalElements + rankingInfo.pageSize - 1) / rankingInfo.pageSize).toInt(),
            )
        }
    }
}
