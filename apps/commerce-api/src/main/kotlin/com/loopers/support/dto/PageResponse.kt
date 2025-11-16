package com.loopers.support.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

data class PageResponse<T>(
    val content: T,
    val pageInfo: PageInfo,
) {
    companion object {
        fun <T> from(
            content: T,
            page: Page<*>,
        ): PageResponse<T> = PageResponse(
            content = content,
            pageInfo = PageInfo(
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            ),
        )
    }
}

@Schema(description = "페이징 정보")
data class PageInfo(
    @Schema(description = "현재 페이지 번호")
    val page: Int,
    @Schema(description = "페이지 크기")
    val size: Int,
    @Schema(description = "전체 요소 개수")
    val totalElements: Long,
    @Schema(description = "전체 페이지 수")
    val totalPages: Int,
)
