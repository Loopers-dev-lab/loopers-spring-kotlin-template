package com.loopers.domain.payment

import com.loopers.domain.payment.config.PaymentFeignConfig
import com.loopers.domain.payment.dto.PaymentDto
import com.loopers.interfaces.api.ApiResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "payment-service",
    url = "\${payment.api-url}",
    configuration = [PaymentFeignConfig::class]
)
interface PaymentFeignClient {

    @PostMapping("/api/v1/payments")
    fun requestPayment(
        @RequestBody request: PaymentDto.Request,
    ): ApiResponse<PaymentDto.Response>
}
