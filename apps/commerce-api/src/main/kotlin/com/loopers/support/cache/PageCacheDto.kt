package com.loopers.support.cache

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

data class PageCacheDto<T>(
    val content: List<T>,
    val number: Int,
    val size: Int,
    val totalElements: Long,
) {
    fun withTotal(total: Long) = copy(totalElements = total)
    fun toPage(): Page<T> =
        PageImpl(content, PageRequest.of(number, size), totalElements)

    companion object {
        fun <T> fromPage(page: Page<T>): PageCacheDto<T> =
            PageCacheDto(
                content = page.content,
                number = page.number,
                size = page.size,
                totalElements = page.totalElements,
            )
    }
}
