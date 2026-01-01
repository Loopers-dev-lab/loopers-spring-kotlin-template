package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingInfo
import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Ranking API V1 명세
 *
 * Swagger 문서 자동 생성을 위한 인터페이스
 */
@Tag(name = "Ranking V1 API", description = "상품 랭킹 관련 API")
interface RankingV1ApiSpec {

    @Operation(
        summary = "기간별 랭킹 조회",
        description = "일간/주간/월간 상품 랭킹을 조회합니다. daily(Redis), weekly/monthly(DB MV)"
    )
    fun getRankings(
        @Parameter(
            description = "랭킹 기간 (daily, weekly, monthly)",
            example = "daily",
            schema = Schema(allowableValues = ["daily", "weekly", "monthly"], defaultValue = "daily")
        )
        period: String,

        @Parameter(
            description = "날짜 (daily: yyyyMMdd, weekly: yyyy-Www, monthly: yyyy-MM)",
            example = "20251231"
        )
        date: String?,

        @Parameter(hidden = true)
        pageable: Pageable
    ): ApiResponse<Page<RankingInfo>>


}
