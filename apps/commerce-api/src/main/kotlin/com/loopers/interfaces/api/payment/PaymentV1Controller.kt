package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.annotation.Authenticated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentV1ApiSpec {
    @Authenticated
    @PostMapping("")
    override fun requestPayment(@RequestBody request: PaymentV1Dto.PaymentRequest): ApiResponse<PaymentV1Dto.PaymentResponse> {
        return paymentFacade.requestPayment(request.toCommand())
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PostMapping("/{id}/process")
    override fun processPayment(@PathVariable id: Long) {
        paymentFacade.processPayment(id)
    }

    @PostMapping("/webhook")
    override fun processPaymentWebhook(@RequestBody request: PaymentV1Dto.WebhookRequest) {
        paymentFacade.processPaymentWebhook(request.toCommand())
    }
}
