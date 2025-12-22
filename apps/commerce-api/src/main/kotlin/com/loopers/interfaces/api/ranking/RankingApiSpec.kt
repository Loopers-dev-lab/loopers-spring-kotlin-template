package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDateTime

@Tag(name = "Ranking" , description = "랭킹 API")
interface RankingApiSpec {

    @Operation(summary = "Ranking 목록 조회" , description = "Redis 의 Sorted Set 을 이용")
    fun getRanking(
        @Parameter(description = "페이징 정보(page, size)") pageable: Pageable,
        @Parameter(description = "yyyyMMdd 날짜 정보")
        @DateTimeFormat(pattern = "yyyyMMdd")
        @RequestParam date: LocalDateTime
    ): ApiResponse<RankingDto.PageResponse>
}
