package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class IssuedCouponPageQuery(
    val userId: Long,
    val page: Int,
    val size: Int,
    val sort: IssuedCouponSortType,
) {
    init {
        if (page < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "page는 0 이상이어야 합니다.")
        }
        if (size <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.")
        }
        if (size > MAX_SIZE) {
            throw CoreException(ErrorType.BAD_REQUEST, "size는 최대 ${MAX_SIZE}까지 가능합니다.")
        }
    }

    companion object {
        private const val MAX_SIZE = 100
        private const val DEFAULT_PAGE = 0
        private const val DEFAULT_SIZE = 20
        private val DEFAULT_SORT = IssuedCouponSortType.LATEST

        fun of(
            userId: Long,
            page: Int? = null,
            size: Int? = null,
            sort: IssuedCouponSortType? = null,
        ): IssuedCouponPageQuery {
            return IssuedCouponPageQuery(
                userId = userId,
                page = page ?: DEFAULT_PAGE,
                size = size ?: DEFAULT_SIZE,
                sort = sort ?: DEFAULT_SORT,
            )
        }
    }
}
