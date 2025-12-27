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
        description = "일자별 상품 랭킹 페이지를 조회합니다.",
    )
    fun getRankings(
        @Parameter(description = "랭킹 날짜 (yyyyMMdd)")
        date: String,
        @Parameter(description = "페이지 번호 (0부터 시작)", schema = Schema(defaultValue = "0"))
        pageable: Pageable,
    ): ApiResponse<PageResponse<RankingV1Dto.RankingListResponse>>
}
