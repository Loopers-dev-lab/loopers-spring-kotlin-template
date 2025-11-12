package com.loopers.interfaces.api.common

data class PageResponseDto<T>(
    val items: List<T>,
    val pagination: PaginationDto,
) {
    data class PaginationDto(
        val pageNumber: Long,
        val pageSize: Long,
        val hasNext: Boolean,
        val totalCount: Long,
    )
}
