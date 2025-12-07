package com.loopers.domain.payment

import com.loopers.support.values.Money

class PaymentCommand {
    data class Create(
        val userId: Long,
        val orderId: Long,
        val totalAmount: Money,
        val usedPoint: Money,
        val issuedCouponId: Long?,
        val couponDiscount: Money,
    )

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
