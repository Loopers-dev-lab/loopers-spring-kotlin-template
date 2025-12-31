package com.loopers.application.ranking

import java.math.BigDecimal

class RankingCriteria {

    /**
     * 랭킹 조회 조건
     *
     * @property date 조회할 시간대 (yyyyMMddHH 형식, null이면 현재 시간대)
     * @property page 페이지 번호 (0-based, default: 0)
     * @property size 페이지 크기 (default: 20)
     */
    data class FindRankings(
        val date: String? = null,
        val page: Int? = null,
        val size: Int? = null,
    ) {
        companion object {
            private const val DEFAULT_PAGE = 0
            private const val DEFAULT_SIZE = 20
        }

        fun resolvedPage(): Int = page ?: DEFAULT_PAGE
        fun resolvedSize(): Int = size ?: DEFAULT_SIZE
    }

    /**
     * 가중치 수정 조건
     *
     * @property viewWeight 조회 가중치 (0.0 ~ 1.0)
     * @property likeWeight 좋아요 가중치 (0.0 ~ 1.0)
     * @property orderWeight 주문 가중치 (0.0 ~ 1.0)
     */
    data class UpdateWeight(
        val viewWeight: BigDecimal,
        val likeWeight: BigDecimal,
        val orderWeight: BigDecimal,
    )
}
