package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader

@Tag(name = "Payment", description = "결제 API")
interface PaymentV1ApiSpec {
    @Operation(summary = "카드 결제 요청", description = "PG를 통한 카드 결제를 요청합니다")
    @PostMapping("/payments/card")
    fun requestCardPayment(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestBody request: PaymentV1Dto.CardPaymentRequest,
    ): ApiResponse<PaymentV1Dto.PaymentResponse>

    @Operation(summary = "결제 콜백", description = "PG로부터 결제 결과를 받습니다")
    @PostMapping("/payments/callback")
    fun paymentCallback(
        @RequestBody callback: PaymentV1Dto.PaymentCallbackRequest,
    ): ApiResponse<Unit>

    @Operation(summary = "결제 정보 조회", description = "거래 키로 결제 정보를 조회합니다")
    @GetMapping("/payments/{transactionKey}")
    fun getPayment(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable transactionKey: String,
    ): ApiResponse<PaymentV1Dto.PaymentDetailResponse>

    @Operation(summary = "주문별 결제 목록 조회", description = "주문 ID로 결제 목록을 조회합니다")
    @GetMapping("/orders/{orderId}/payments")
    fun getPaymentsByOrderId(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable orderId: Long,
    ): ApiResponse<List<PaymentV1Dto.PaymentDetailResponse>>
}
