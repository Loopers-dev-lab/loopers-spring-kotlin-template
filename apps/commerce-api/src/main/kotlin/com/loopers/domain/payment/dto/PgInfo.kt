package com.loopers.domain.payment.dto

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus

object PgInfo {

    data class Order(
        val orderId: String,
        val transactions: List<Transaction>,
    )

    data class Transaction(
        val transactionKey: String,
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val status: PaymentStatus,
        val reason: String?,
    )
}
