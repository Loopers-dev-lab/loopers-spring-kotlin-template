package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class PageQuery(
    val page: Int,
    val size: Int,
    val sort: ProductSortType,
    val brandId: Long?,
) {
    init {
        if (page < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "page는 0 이상이어야 합니다.")
        }
        if (size <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.")
        }
    }

    companion object {
        private const val DEFAULT_PAGE = 0
        private const val DEFAULT_SIZE = 20
        private val DEFAULT_SORT = ProductSortType.LATEST

        fun of(
            page: Int? = null,
            size: Int? = null,
            sort: ProductSortType? = null,
            brandId: Long? = null,
        ): PageQuery {
            return PageQuery(
                page = page ?: DEFAULT_PAGE,
                size = size ?: DEFAULT_SIZE,
                sort = sort ?: DEFAULT_SORT,
                brandId = brandId,
            )
        }
    }
}
