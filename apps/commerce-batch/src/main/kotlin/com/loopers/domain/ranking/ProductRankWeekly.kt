package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "mv_product_rank_weekly")
class ProductRankWeekly(

    /**
     *  상품 ID
     */
    @Column(nullable = false)
    val refProductId: Long,

    /**
     * 상품 점수
     */
    @Column(nullable = false)
    val score: Long,

    /**
     *  좋아요 수
     */
    @Column(nullable = false)
    val likeCount: Long,

    /**
     *  조회 수
     */
    @Column(nullable = false)
    val viewCount: Long,

    /**
     * 판매 수
     */
    @Column(nullable = false)
    val salesCount: Long,
) : BaseEntity() {

    companion object {
        fun create(
            refProductId: Long,
            score: Long = 0L,
            likeCount: Long = 0L,
            viewCount: Long = 0L,
            salesCount: Long = 0L,
        ): ProductRankWeekly {
            return ProductRankWeekly(
                refProductId = refProductId,
                score = score,
                likeCount = likeCount,
                viewCount = viewCount,
                salesCount = salesCount,
            )
        }
    }
}
