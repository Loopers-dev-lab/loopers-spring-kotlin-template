package com.loopers.application.ranking

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.product.Product
import com.loopers.domain.ranking.Ranking
import com.loopers.domain.ranking.TimeWindow
import java.math.BigDecimal

/**
 * 랭킹 페이지 정보
 */
data class RankingPageInfo(
    val rankings: List<RankingItemInfo>,
    val window: TimeWindow,
    val timestamp: String,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

/**
 * 랭킹 항목 정보
 */
data class RankingItemInfo(val rank: Int, val score: Double, val product: RankingProductInfo) {
    companion object {
        fun from(ranking: Ranking, product: Product): RankingItemInfo = RankingItemInfo(
            rank = ranking.rank,
            score = ranking.score.value,
            product = RankingProductInfo.from(product),
        )
    }
}

/**
 * 랭킹 응답용 상품 정보
 */
data class RankingProductInfo(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val currency: String,
    val brand: BrandInfo,
    val likeCount: Long,
) {
    companion object {
        fun from(product: Product): RankingProductInfo = RankingProductInfo(
            id = product.id,
            name = product.name,
            price = product.price.amount,
            currency = product.price.currency.name,
            brand = BrandInfo.from(product.brand),
            likeCount = product.likeCount,
        )
    }
}
