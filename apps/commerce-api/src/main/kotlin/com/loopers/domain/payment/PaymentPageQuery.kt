package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime

data class PaymentPageQuery(
    val page: Int,
    val size: Int,
    val sort: PaymentSortType,
    val statuses: List<PaymentStatus>,
    val createdBefore: ZonedDateTime? = null,
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
        private val DEFAULT_SORT = PaymentSortType.CREATED_AT_ASC

        fun of(
            page: Int? = null,
            size: Int? = null,
            sort: PaymentSortType? = null,
            statuses: List<PaymentStatus> = emptyList(),
            createdBefore: ZonedDateTime? = null,
        ): PaymentPageQuery {
            return PaymentPageQuery(
                page = page ?: DEFAULT_PAGE,
                size = size ?: DEFAULT_SIZE,
                sort = sort ?: DEFAULT_SORT,
                statuses = statuses,
                createdBefore = createdBefore,
            )
        }
    }
}
