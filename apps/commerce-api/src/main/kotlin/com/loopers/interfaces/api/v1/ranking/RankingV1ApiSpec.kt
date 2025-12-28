package com.loopers.interfaces.api.v1.ranking

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.dto.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Ranking V1 API", description = "랭킹 조회 API")
interface RankingV1ApiSpec {
    @Operation(
        summary = "랭킹 페이지 조회",
        description = "기간과 날짜 기준 상품 랭킹 페이지를 조회합니다.",
    )
    fun getRankings(
        @Parameter(description = "랭킹 기간 (daily, weekly, monthly)", schema = Schema(defaultValue = "daily"))
        period: String,
        @Parameter(description = "랭킹 기준 날짜 (yyyyMMdd), weekly는 해당 주, monthly는 해당 월 기준")
        date: String,
        @Parameter(description = "페이지 번호 (0부터 시작)", schema = Schema(defaultValue = "0"))
        pageable: Pageable,
    ): ApiResponse<PageResponse<RankingV1Dto.RankingListResponse>>
}
