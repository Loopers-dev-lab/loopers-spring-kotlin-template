package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingCommand
import com.loopers.application.ranking.RankingFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/rankings")
class RankingController(
    private val rankingFacade: RankingFacade,
) : RankingApiSpec {
    @GetMapping
    override fun getRanking(
        @PageableDefault(size = 10) pageable: Pageable,
        @RequestParam date: LocalDateTime,
    ): ApiResponse<RankingDto.PageResponse> {
        val command = RankingCommand.GetRankings.toCommand(pageable, date)
        val rankingInfo = rankingFacade.getRanking(command)
        val response = RankingDto.PageResponse.from(rankingInfo)
        return ApiResponse.success(response)
    }
}
