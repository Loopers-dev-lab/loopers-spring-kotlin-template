package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingFacade
import com.loopers.application.ranking.RankingInfo
import com.loopers.application.ranking.RankingPeriod
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 랭킹 API (V1)
 *
 * 제공 기능:
 * 일간/주간/월간 랭킹 조회 (페이지네이션)
 */
@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingFacade: RankingFacade
) : RankingV1ApiSpec {

    /**
     * 일간/주간/월간 랭킹 조회
     */
    @GetMapping
    override fun getRankings(
        @RequestParam(required = false, defaultValue = "daily") period: String,
        @RequestParam(required = false) date: String?,
        pageable: Pageable
    ): ApiResponse<Page<RankingInfo>> {
        val rankingPeriod = RankingPeriod.from(period)
        val rankings = rankingPeriod.getRankings(date, pageable, rankingFacade)
        return ApiResponse.success(rankings)
    }
}
