package com.loopers.domain.pg

import com.loopers.support.values.Money

data class PgPaymentRequest(
    val orderId: Long,
    val amount: Money,
    val cardInfo: CardInfo,
    val callbackUrl: String,
)
