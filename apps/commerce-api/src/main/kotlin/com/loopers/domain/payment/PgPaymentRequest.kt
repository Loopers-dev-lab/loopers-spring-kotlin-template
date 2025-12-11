package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money

data class PgPaymentRequest(
    val paymentId: Long,
    val amount: Money,
    val cardInfo: CardInfo? = null,
) {
    init {
        if (amount < Money.ZERO_KRW) {
            throw CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청 금액은 0 이상이어야 합니다")
        }
    }
}
