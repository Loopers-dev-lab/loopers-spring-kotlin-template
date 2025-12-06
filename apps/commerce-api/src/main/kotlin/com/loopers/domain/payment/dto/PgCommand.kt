package com.loopers.domain.payment.dto

import com.loopers.domain.payment.CardType

object PgCommand {
    data class Request(
        val userId: String,
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
    )
}
