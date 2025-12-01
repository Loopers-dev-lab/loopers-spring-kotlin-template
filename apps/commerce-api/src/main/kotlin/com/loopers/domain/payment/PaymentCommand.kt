package com.loopers.domain.payment

object PaymentCommand {

    data class Request(
        val userId: String,
        val orderId: String,
        val cardType: CardType?,
        val cardNo: String?,
        val amount: Long,
    )
}
