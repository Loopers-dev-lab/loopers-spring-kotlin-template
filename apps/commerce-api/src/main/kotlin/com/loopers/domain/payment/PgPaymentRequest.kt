package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money

data class PgPaymentRequest(
    val orderId: Long,
    val amount: Money,
    val cardInfo: CardInfo,
) {
    init {
        if (amount <= Money.ZERO_KRW) {
            throw CoreException(ErrorType.BAD_REQUEST, "PG 결제 요청 금액은 0보다 커야 합니다")
        }
    }
}
