package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment Webhook V1 API", description = "PG 결제 콜백 API 입니다.")
interface PaymentWebhookV1ApiSpec {
    @Operation(
        summary = "결제 콜백",
        description = "PG로부터 결제 콜백을 수신하고, PG에서 실제 트랜잭션 정보를 조회하여 처리합니다.",
    )
    fun handleCallback(
        request: PaymentWebhookV1Request.Callback,
    ): ApiResponse<Unit>
}
