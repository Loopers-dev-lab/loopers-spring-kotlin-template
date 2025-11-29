package com.loopers.interfaces.api.common

import io.swagger.v3.oas.annotations.media.Schema

data class PageRequestDto(
    @get:Schema(description = "페이지 번호 (0-based)", example = "0", required = true)
    val pageNumber: Int,

    @get:Schema(description = "페이지 크기", example = "10", required = true)
    val pageSize: Int,

    @get:Schema(
        description = "정렬 조건 리스트 (예: [{\"field\": \"createdAt\", \"direction\": \"DESC\"}])",
        example = "[{\"field\": \"createdAt\", \"direction\": \"DESC\"}]",
    )
    val sort: List<SortCondition> = emptyList(),

    @get:Schema(description = "브랜드 ID 필터", example = "1")
    val brandId: Long? = null,
) {
    init {
        require(pageNumber >= 0) { "페이지 번호는 0 이상이어야 합니다." }
        require(pageSize > 0) { "페이지 크기는 1 이상이어야 합니다." }
        require(pageSize <= 100) { "페이지 크기는 100 이하여야 합니다." }
    }

    data class SortCondition(
        @get:Schema(description = "정렬 필드명", example = "createdAt")
        val field: String,

        @get:Schema(description = "정렬 방향 (ASC, DESC)", example = "DESC")
        val direction: SortDirection,
    )

    enum class SortDirection {
        ASC,
        DESC,
    }
}
