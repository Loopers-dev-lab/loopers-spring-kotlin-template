package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.auth.annotation.Authenticated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentV1ApiSpec {
    @Authenticated
    @GetMapping("")
    override fun requestPayment(request: PaymentV1Dto.PaymentRequest): ApiResponse<PaymentV1Dto.PaymentResponse> {
        return paymentFacade.requestPayment(request.toCommand())
            .let { PaymentV1Dto.PaymentResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @Authenticated
    @GetMapping("/{id}/process")
    override fun processPayment(@PathVariable id: Long) {
        return paymentFacade.processPayment(id)
    }
}
