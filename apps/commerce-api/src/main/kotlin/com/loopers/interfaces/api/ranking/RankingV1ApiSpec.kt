package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Ranking V1 API", description = "Loopers Ranking API 입니다.")
interface RankingV1ApiSpec {

    @Operation(
        summary = "인기 상품 랭킹 조회",
        description = "인기 상품 랭킹을 페이지네이션으로 조회합니다.",
    )
    fun getRankings(
        @Parameter(
            name = "period",
            description = "조회 기간 (hourly/daily, 선택, 기본값: hourly)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        period: String?,
        @Parameter(
            name = "date",
            description = "조회할 시간대 (hourly: yyyyMMddHH, daily: yyyyMMdd 형식, 선택, 기본값: 현재 시간대)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        date: String?,
        @Parameter(
            name = "page",
            description = "페이지 번호 (0부터 시작) (선택, 기본값: 0)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        page: Int?,
        @Parameter(
            name = "size",
            description = "페이지 크기 (1~100) (선택, 기본값: 20)",
            required = false,
            `in` = ParameterIn.QUERY,
        )
        size: Int?,
    ): ApiResponse<RankingV1Response.GetRankings>

    @Operation(
        summary = "랭킹 가중치 조회",
        description = "현재 설정된 랭킹 가중치를 조회합니다.",
    )
    fun getWeight(): ApiResponse<RankingV1Response.GetWeight>

    @Operation(
        summary = "랭킹 가중치 수정",
        description = "랭킹 가중치를 수정합니다. 관리자 전용 API입니다.",
    )
    fun updateWeight(
        request: RankingV1Request.UpdateWeight,
    ): ApiResponse<RankingV1Response.UpdateWeight>
}
