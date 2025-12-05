package com.loopers.domain.payment

import com.loopers.support.values.Money

data class PgTransaction(
    val transactionKey: String,
    val orderId: Long,
    val cardType: CardType,
    val cardNo: String,
    val amount: Money,
    val status: PgTransactionStatus,
    val failureReason: String? = null,
)
