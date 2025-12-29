package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 랭킹 API (V1)
 *
 * 제공 기능:
 * 1. 일간 랭킹 조회 (페이지네이션)
 */
@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingFacade: RankingFacade
) : RankingV1ApiSpec {

    /**
     * 일간 랭킹 조회
     *
     * 예시:
     * - GET /api/v1/rankings?date=20251222&page=0&size=20
     * - GET /api/v1/rankings (오늘 날짜, 첫 페이지)
     *
     * @param date 날짜 (yyyyMMdd 형식, 기본값: 오늘)
     * @param page 페이지 번호 (0부터 시작, 기본값: 0)
     * @param size 페이지 크기 (기본값: 20)
     * @return 랭킹 정보 페이지 (상품 정보 포함)
     */
    @GetMapping
    override fun getRankings(
        @RequestParam(required = false) date: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<Page<RankingV1Dto.RankingResponse>> {
        // 페이지 파라미터 검증 및 제한 (DoS 방지)
        val validPage = page.coerceAtLeast(0)
        val validSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(validPage, validSize)

        val rankings = rankingFacade.getRankings(date, pageable)

        return ApiResponse.success(
            rankings.map { RankingV1Dto.RankingResponse.from(it) }
        )
    }
}
