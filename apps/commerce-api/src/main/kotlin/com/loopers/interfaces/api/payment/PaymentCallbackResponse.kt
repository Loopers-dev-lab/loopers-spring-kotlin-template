package com.loopers.interfaces.api.payment

/**
 * PG 콜백 응답 DTO
 */
data class PaymentCallbackResponse(
    val result: String,
    val message: String?,
) {
    companion object {
        fun success(): PaymentCallbackResponse = PaymentCallbackResponse(
            result = "OK",
            message = null,
        )

        fun alreadyProcessed(): PaymentCallbackResponse = PaymentCallbackResponse(
            result = "OK",
            message = "이미 처리된 결제입니다",
        )

        fun error(message: String): PaymentCallbackResponse = PaymentCallbackResponse(
            result = "ERROR",
            message = message,
        )
    }
}
