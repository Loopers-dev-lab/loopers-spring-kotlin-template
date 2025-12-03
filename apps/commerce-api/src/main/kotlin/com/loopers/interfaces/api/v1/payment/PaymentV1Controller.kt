package com.loopers.interfaces.api.v1.payment

import com.loopers.application.payment.PaymentFacade
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/payments")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
) {

    private val log = LoggerFactory.getLogger(PaymentV1Controller::class.java)

    @PostMapping
    fun callback(
        @RequestBody request: PaymentV1Dto.CallbackRequest,
    ) {
        log.info("Received callback request for payment $request")
        paymentFacade.callback(request.toCommand())
    }
}
