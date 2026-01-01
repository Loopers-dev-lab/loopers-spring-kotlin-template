package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

/**
 * MvProductRankMonthly 엔티티 - 월간 상품 인기 랭킹
 *
 * - 30일 동안의 상품별 인기 점수를 기반으로 한 랭킹 저장
 * - (base_date, rank) 조합이 고유
 * - rank는 1부터 시작 (1~100)
 * - score는 음수가 될 수 없음
 */
@Entity
@Table(
    name = "mv_product_rank_monthly",
    indexes = [
        Index(name = "uk_monthly_rank", columnList = "base_date, `rank`", unique = true),
    ],
)
class MvProductRankMonthly(
    @Column(name = "base_date", nullable = false)
    val baseDate: LocalDate,

    @Column(name = "`rank`", nullable = false)
    val rank: Int,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "score", nullable = false, precision = 15, scale = 2)
    val score: BigDecimal,
) : BaseEntity() {

    init {
        validateFields()
    }

    private fun validateFields() {
        require(rank in 1..100) { "rank는 1부터 100 사이의 값이어야 합니다: $rank" }
        require(score >= BigDecimal.ZERO) { "score는 음수가 될 수 없습니다: $score" }
    }

    companion object {
        fun create(
            baseDate: LocalDate,
            rank: Int,
            productId: Long,
            score: BigDecimal,
        ): MvProductRankMonthly {
            return MvProductRankMonthly(
                baseDate = baseDate,
                rank = rank,
                productId = productId,
                score = score,
            )
        }
    }
}
