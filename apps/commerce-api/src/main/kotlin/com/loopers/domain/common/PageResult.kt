package com.loopers.domain.common

import org.springframework.data.domain.Page

data class PageResult<T>(
    val data: List<T>,
    val page: PageInfo,
) {
    data class PageInfo(
        val number: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: Page<*>): PageInfo {
                return PageInfo(page.number, page.size, page.totalElements, page.totalPages)
            }
        }
    }

    companion object {
        fun <T, R> from(page: Page<T>, mapper: (T) -> R): PageResult<R> {
            return PageResult(page.content.map(mapper), PageInfo.from(page))
        }
    }
}
