package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "User V1 API", description = "User API 입니다.")
interface PaymentV1ApiSpec {
    @Operation(
        summary = "내 유저 조회",
        description = "ID로 내 유저를 조회합니다.",
    )
    fun requestPayment(request: PaymentV1Dto.PaymentRequest): ApiResponse<PaymentV1Dto.PaymentResponse>

    fun processPayment(id: Long)
}
