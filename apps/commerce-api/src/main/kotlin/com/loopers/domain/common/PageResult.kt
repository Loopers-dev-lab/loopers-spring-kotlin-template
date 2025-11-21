package com.loopers.domain.common

data class PageResult<T>(
    val items: List<T>,
    val pageNumber: Long,
    val pageSize: Long,
    val hasNext: Boolean,
    val totalCount: Long,
)
