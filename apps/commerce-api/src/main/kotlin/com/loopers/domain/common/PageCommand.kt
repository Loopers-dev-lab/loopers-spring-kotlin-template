package com.loopers.domain.common

data class PageCommand(
    val pageNumber: Long,
    val pageSize: Long,
    val sort: List<SortCondition> = emptyList(),
    val brandId: Long? = null,
) {
    init {
        require(pageNumber >= 0) { "페이지 번호는 0 이상이어야 합니다." }
        require(pageSize > 0) { "페이지 크기는 1 이상이어야 합니다." }
        require(pageSize <= 100) { "페이지 크기는 100 이하여야 합니다." }
    }

    val offset: Long = pageNumber * pageSize

    fun cacheKey(): String {
        return "${brandId}_${pageNumber}_${pageSize}_$sort"
    }

    fun hasNext(totalCount: Long): Boolean {
        return (this.pageNumber + 1) * this.pageSize < totalCount
    }

    data class SortCondition(
        val field: String,
        val direction: SortDirection,
    )

    enum class SortDirection {
        ASC,
        DESC,
    }
}
