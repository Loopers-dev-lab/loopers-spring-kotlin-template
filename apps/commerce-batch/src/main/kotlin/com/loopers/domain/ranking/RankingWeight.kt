package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * RankingWeight 엔티티 - 랭킹 가중치 설정 (읽기 전용)
 *
 * - commerce-api와 동일한 테이블(ranking_weight)을 참조
 * - 읽기 전용으로 사용 (수정은 commerce-api에서만 수행)
 */
@Entity
@Table(name = "ranking_weight")
class RankingWeight(
    @Column(name = "view_weight", nullable = false, precision = 3, scale = 2)
    val viewWeight: BigDecimal,

    @Column(name = "like_weight", nullable = false, precision = 3, scale = 2)
    val likeWeight: BigDecimal,

    @Column(name = "order_weight", nullable = false, precision = 3, scale = 2)
    val orderWeight: BigDecimal,
) : BaseEntity() {

    companion object {
        /**
         * 가중치 조회 실패 시 사용하는 기본값
         */
        fun fallback(): RankingWeight {
            return RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )
        }
    }
}
