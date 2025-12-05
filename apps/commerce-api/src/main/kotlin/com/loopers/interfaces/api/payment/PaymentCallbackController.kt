package com.loopers.interfaces.api.payment

import com.loopers.domain.payment.PaymentCallbackDto
import com.loopers.domain.payment.PaymentCallbackService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentCallbackController(
    private val paymentCallbackService: PaymentCallbackService
) {

    @PostMapping("/callback")
    fun handleCallback(@RequestBody callback: PaymentCallbackDto): ApiResponse<Unit> {
        paymentCallbackService.handlePaymentCallback(callback)
        return ApiResponse.success(Unit)
    }
}
