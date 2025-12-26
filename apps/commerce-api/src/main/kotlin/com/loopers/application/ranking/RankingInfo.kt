package com.loopers.application.ranking

import com.loopers.domain.product.ProductView
import com.loopers.domain.ranking.RankingWeight
import com.loopers.support.values.Money
import java.math.BigDecimal

class RankingInfo {

    /**
     * 랭킹 조회 결과
     *
     * @property rankings 랭킹 목록
     * @property hasNext 다음 페이지 존재 여부
     */
    data class FindRankings(
        val rankings: List<RankingUnit>,
        val hasNext: Boolean,
    )

    /**
     * 랭킹 단위 정보
     *
     * @property rank 순위 (1-based)
     * @property productId 상품 ID
     * @property name 상품명
     * @property price 상품 가격
     * @property stock 재고 수량
     * @property brandId 브랜드 ID
     * @property brandName 브랜드명
     * @property likeCount 좋아요 수
     */
    data class RankingUnit(
        val rank: Int,
        val productId: Long,
        val name: String,
        val price: Money,
        val stock: Int,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
    ) {
        companion object {
            fun from(
                rank: Int,
                productView: ProductView,
            ): RankingUnit {
                return RankingUnit(
                    rank = rank,
                    productId = productView.productId,
                    name = productView.productName,
                    price = productView.price,
                    stock = productView.stockQuantity,
                    brandId = productView.brandId,
                    brandName = productView.brandName,
                    likeCount = productView.likeCount,
                )
            }
        }
    }

    /**
     * 가중치 조회 결과
     *
     * @property viewWeight 조회 가중치
     * @property likeWeight 좋아요 가중치
     * @property orderWeight 주문 가중치
     */
    data class FindWeight(
        val viewWeight: BigDecimal,
        val likeWeight: BigDecimal,
        val orderWeight: BigDecimal,
    ) {
        companion object {
            fun from(weight: RankingWeight): FindWeight {
                return FindWeight(
                    viewWeight = weight.viewWeight,
                    likeWeight = weight.likeWeight,
                    orderWeight = weight.orderWeight,
                )
            }
        }
    }

    /**
     * 가중치 수정 결과
     *
     * @property viewWeight 조회 가중치
     * @property likeWeight 좋아요 가중치
     * @property orderWeight 주문 가중치
     */
    data class UpdateWeight(
        val viewWeight: BigDecimal,
        val likeWeight: BigDecimal,
        val orderWeight: BigDecimal,
    ) {
        companion object {
            fun from(weight: RankingWeight): UpdateWeight {
                return UpdateWeight(
                    viewWeight = weight.viewWeight,
                    likeWeight = weight.likeWeight,
                    orderWeight = weight.orderWeight,
                )
            }
        }
    }
}
