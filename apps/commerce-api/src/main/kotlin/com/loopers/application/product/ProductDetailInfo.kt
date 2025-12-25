package com.loopers.application.product

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.product.Product
import com.loopers.domain.product.Stock
import com.loopers.domain.ranking.Ranking
import com.loopers.domain.ranking.TimeWindow
import java.math.BigDecimal

data class ProductDetailInfo(
    val id: Long,
    val name: String,
    val price: BigDecimal,
    val currency: String,
    val brand: BrandInfo,
    val stockQuantity: Int,
    val likeCount: Long,
    val ranking: ProductRankingInfo?,
) {
    companion object {
        fun from(
            product: Product,
            stock: Stock,
            likeCount: Long,
            dailyRanking: Ranking? = null,
        ): ProductDetailInfo = ProductDetailInfo(
            id = product.id,
            name = product.name,
            price = product.price.amount,
            currency = product.price.currency.name,
            brand = BrandInfo.from(product.brand),
            stockQuantity = stock.quantity,
            likeCount = likeCount,
            ranking = dailyRanking?.let {
                ProductRankingInfo(
                    rank = it.rank,
                    score = it.score.value,
                    window = TimeWindow.DAILY,
                )
            },
        )
    }
}

/**
 * 상품 랭킹 정보
 */
data class ProductRankingInfo(
    /**
     * 순위
     */
    val rank: Int,

    /**
     * 점수
     */
    val score: Double,

    /**
     * 시간 윈도우
     */
    val window: TimeWindow,
)
