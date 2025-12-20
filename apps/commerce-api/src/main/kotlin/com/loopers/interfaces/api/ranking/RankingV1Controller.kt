package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingFacade
import com.loopers.domain.ranking.TimeWindow
import com.loopers.interfaces.api.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 랭킹 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingFacade: RankingFacade,
) : RankingV1ApiSpec {

    /**
     * 랭킹 페이지 조회
     *
     * GET /api/v1/rankings?window=DAILY&date=20250906&page=1&size=20
     * GET /api/v1/rankings?window=HOURLY&date=2025090614&page=1&size=20
     */
    @GetMapping
    override fun getRankings(
        @RequestParam(defaultValue = "DAILY") window: String,
        @RequestParam(required = false) date: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<RankingV1Dto.RankingPageResponse> {
        val timeWindow = try {
            TimeWindow.valueOf(window.uppercase())
        } catch (e: IllegalArgumentException) {
            val validValues = TimeWindow.entries.joinToString(", ") { it.name }
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "잘못된 window 값입니다. 가능한 값: $validValues, 입력값: $window",
                e,
            )
        }
        return RankingV1Dto.RankingPageResponse.from(
            rankingFacade.getRankingPage(timeWindow, date, page, size),
        ).let { ApiResponse.success(it) }
    }
}
