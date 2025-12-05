package com.loopers.interfaces.api.payment

/**
 * PG 콜백 요청 DTO
 */
data class PaymentCallbackRequest(
    val transactionKey: String,
    val orderId: String,
    val status: String,
    val reason: String?,
) {
    fun isSuccess(): Boolean = status == "SUCCESS"
}
