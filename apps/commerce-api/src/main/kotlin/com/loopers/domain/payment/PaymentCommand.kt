package com.loopers.domain.payment

class PaymentCommand {
    data class FindPayments(
        val page: Int? = null,
        val size: Int? = null,
        val sort: PaymentSortType? = null,
        val statuses: List<PaymentStatus> = emptyList(),
    ) {
        fun toQuery(): PaymentPageQuery {
            return PaymentPageQuery.of(
                page = page,
                size = size,
                sort = sort,
                statuses = statuses,
            )
        }
    }
}
