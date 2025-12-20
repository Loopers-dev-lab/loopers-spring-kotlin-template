package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Ranking V1 API", description = "실시간 랭킹 조회 API")
interface RankingV1ApiSpec {
    @Operation(
        summary = "랭킹 페이지 조회",
        description = """
            시간 윈도우별 상품 랭킹을 조회합니다.

            - DAILY: 일간 랭킹 (yyyyMMdd 형식)
            - HOURLY: 시간별 랭킹 (yyyyMMddHH 형식)

            페이지네이션을 지원하며, 랭킹 정보와 상품 정보를 함께 제공합니다.
        """,
    )
    fun getRankings(
        @Parameter(
            description = "시간 윈도우 (DAILY: 일간, HOURLY: 시간별)",
            schema = Schema(defaultValue = "DAILY"),
        )
        window: String,
        @Parameter(
            description = "조회할 날짜/시간 (yyyyMMdd 또는 yyyyMMddHH). 미지정 시 현재 시각 사용",
        )
        date: String?,
        @Parameter(
            description = "페이지 번호 (1부터 시작)",
            schema = Schema(defaultValue = "1"),
        )
        page: Int,
        @Parameter(
            description = "페이지 크기",
            schema = Schema(defaultValue = "20"),
        )
        size: Int,
    ): ApiResponse<RankingV1Dto.RankingPageResponse>
}
