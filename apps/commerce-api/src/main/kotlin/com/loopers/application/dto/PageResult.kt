package com.loopers.application.dto

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T> from(page: Page<T>): PageResult<T> {
            return PageResult(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    fun toPage(pageable: Pageable): Page<T> {
        return PageImpl(content, pageable, totalElements)
    }
}
