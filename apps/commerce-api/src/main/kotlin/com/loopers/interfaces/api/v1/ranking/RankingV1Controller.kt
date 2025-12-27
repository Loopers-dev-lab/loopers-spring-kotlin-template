package com.loopers.interfaces.api.v1.ranking

import com.loopers.application.ranking.RankingFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.dto.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingFacade: RankingFacade,
) : RankingV1ApiSpec {

    @GetMapping
    override fun getRankings(
        @RequestParam date: String,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<RankingV1Dto.RankingListResponse>> {
        val rankingPage = rankingFacade.getRankings(date, pageable)

        return PageResponse.from(
            content = RankingV1Dto.RankingListResponse.from(rankingPage.content),
            page = rankingPage,
        ).let { ApiResponse.success(it) }
    }
}
