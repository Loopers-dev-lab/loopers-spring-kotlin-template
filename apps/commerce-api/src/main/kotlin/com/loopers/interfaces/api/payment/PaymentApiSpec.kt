package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment", description = "결제 확인 API")
interface PaymentApiSpec {

    @Operation(summary = "결제 콜백", description = "PG 시스템으로부터 결제 결과를 수신합니다")
    fun handlePaymentCallback(request: PaymentDto.CallbackRequest): ApiResponse<PaymentDto.CallbackResponse>
}
