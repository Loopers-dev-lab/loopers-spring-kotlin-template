package com.loopers.interfaces.api.ranking

import com.loopers.application.brand.BrandInfo
import com.loopers.application.ranking.RankingItemInfo
import com.loopers.application.ranking.RankingPageInfo
import com.loopers.application.ranking.RankingProductInfo
import com.loopers.domain.ranking.TimeWindow
import java.math.BigDecimal

class RankingV1Dto {
    /**
     * 랭킹 페이지 응답
     */
    data class RankingPageResponse(
        /**
         * 랭킹 목록
         */
        val rankings: List<RankingItemResponse>,

        /**
         * 시간 윈도우 (DAILY, HOURLY)
         */
        val window: TimeWindow,

        /**
         * 날짜 (yyyyMMdd 또는 yyyyMMddHH)
         */
        val timestamp: String,

        /**
         * 페이지 정보
         */
        val page: Int,
        val size: Int,
        val totalCount: Long,
    ) {
        companion object {
            fun from(info: RankingPageInfo): RankingPageResponse = RankingPageResponse(
                rankings = info.rankings.map { RankingItemResponse.from(it) },
                window = info.window,
                timestamp = info.timestamp,
                page = info.page,
                size = info.size,
                totalCount = info.totalCount,
            )
        }
    }

    /**
     * 랭킹 항목 응답
     */
    data class RankingItemResponse(
        /**
         * 순위
         */
        val rank: Int,

        /**
         * 점수
         */
        val score: Double,

        /**
         * 상품 정보
         */
        val product: RankingProductResponse,
    ) {
        companion object {
            fun from(info: RankingItemInfo): RankingItemResponse = RankingItemResponse(
                rank = info.rank,
                score = info.score,
                product = RankingProductResponse.from(info.product),
            )
        }
    }

    /**
     * 랭킹 응답용 상품 정보
     */
    data class RankingProductResponse(
        val id: Long,
        val name: String,
        val price: BigDecimal,
        val currency: String,
        val brand: BrandInfo,
        val likeCount: Long,
    ) {
        companion object {
            fun from(info: RankingProductInfo): RankingProductResponse = RankingProductResponse(
                id = info.id,
                name = info.name,
                price = info.price,
                currency = info.currency,
                brand = info.brand,
                likeCount = info.likeCount,
            )
        }
    }
}
