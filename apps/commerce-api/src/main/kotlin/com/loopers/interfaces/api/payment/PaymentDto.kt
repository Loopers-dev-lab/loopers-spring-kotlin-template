package com.loopers.interfaces.api.payment

class PaymentDto {
    data class CallbackRequest(
        val transactionKey: String,
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?,
    )

    data class CallbackResponse(val message: String)
}
