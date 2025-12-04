package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payment")
class PaymentController(private val paymentFacade: PaymentFacade) : PaymentApiSpec {

    companion object {
        private val logger = LoggerFactory.getLogger(PaymentController::class.java)
    }

    @PostMapping("/callback")
    override fun handlePaymentCallback(
        @RequestBody request: PaymentDto.CallbackRequest,
    ): ApiResponse<PaymentDto.CallbackResponse> {
        logger.info("Payment callback received: transactionKey=${request.transactionKey}, status=${request.status}")

        paymentFacade.handleCallback(request.transactionKey, request.status)

        return ApiResponse.success(
            PaymentDto.CallbackResponse(
                message = "Callback processed successfully",
            ),
        )
    }
}
