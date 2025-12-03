package com.loopers.domain.payment.dto

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus

object PaymentCommand {

    data class Create(
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val orderId: Long,
        val userId: Long,
    )

    data class Callback(
        val transactionKey: String,
        val orderId: String,
        val status: PaymentStatus,
        val reason: String?,
    )
}
