package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page

/**
 * Ranking API V1 명세
 *
 * Swagger 문서 자동 생성을 위한 인터페이스
 */
@Tag(name = "Ranking V1 API", description = "상품 랭킹 관련 API")
interface RankingV1ApiSpec {

    @Operation(
        summary = "일간 랭킹 조회",
        description = "특정 날짜의 상품 랭킹을 페이지 단위로 조회합니다. 상품 정보가 함께 반환되며, 페이지 크기는 최대 100개로 제한됩니다."
    )
    fun getRankings(
        @Parameter(description = "날짜 (yyyyMMdd 형식), 미입력 시 오늘 날짜", example = "20251223")
        date: String?,

        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        page: Int,

        @Parameter(description = "페이지 크기 (최대 100)", example = "20")
        size: Int
    ): ApiResponse<Page<RankingV1Dto.RankingResponse>>
}
