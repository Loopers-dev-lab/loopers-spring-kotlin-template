package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingCriteria
import com.loopers.application.ranking.RankingFacade
import com.loopers.interfaces.api.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
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
        @RequestParam(required = false) period: String?,
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<RankingV1Response.GetRankings> {
        val criteria = RankingCriteria.FindRankings(
            period = period,
            date = date,
            page = page,
            size = size,
        )
        return rankingFacade.findRankings(criteria)
            .let { RankingV1Response.GetRankings.from(it) }
            .let { ApiResponse.success(it) }
    }

    @GetMapping("/weight")
    override fun getWeight(): ApiResponse<RankingV1Response.GetWeight> {
        return rankingFacade.findWeight()
            .let { RankingV1Response.GetWeight.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PutMapping("/weight")
    override fun updateWeight(
        @RequestBody @Valid request: RankingV1Request.UpdateWeight,
    ): ApiResponse<RankingV1Response.UpdateWeight> {
        val criteria = request.toCriteria()
        return rankingFacade.updateWeight(criteria)
            .let { RankingV1Response.UpdateWeight.from(it) }
            .let { ApiResponse.success(it) }
    }
}
