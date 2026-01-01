package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingCommand
import com.loopers.domain.ranking.RankingPeriod
import java.math.BigDecimal

class RankingCriteria {

    /**
     * 랭킹 조회 조건
     *
     * @property period 조회 기간 (hourly/daily, default: hourly)
     * @property date 조회할 시간대 (hourly: yyyyMMddHH, daily: yyyyMMdd 형식, null이면 현재 시간대)
     * @property page 페이지 번호 (0-based, default: 0)
     * @property size 페이지 크기 (default: 20)
     */
    data class FindRankings(
        val period: String? = null,
        val date: String? = null,
        val page: Int? = null,
        val size: Int? = null,
    ) {
        /**
         * Converts to RankingCommand.FindRankings
         */
        fun toCommand(): RankingCommand.FindRankings {
            return RankingCommand.FindRankings(
                period = RankingPeriod.fromString(period),
                date = date,
                page = page ?: DEFAULT_PAGE,
                size = size ?: DEFAULT_SIZE,
            )
        }

        companion object {
            private const val DEFAULT_PAGE = 0
            private const val DEFAULT_SIZE = 20
        }
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
