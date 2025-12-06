package com.loopers.interfaces.api.payment

import io.swagger.v3.oas.annotations.media.Schema

/**
 * PG Webhook 요청 DTO
 */
class PaymentWebhookV1Request {
    @Schema(description = "PG 결제 콜백 요청")
    data class Callback(
        @field:Schema(description = "주문 ID")
        val orderId: Long,
        @field:Schema(description = "PG 외부 결제 키")
        val externalPaymentKey: String,
    )
}
