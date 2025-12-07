package com.loopers.infrastructure.payment

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "pg-payment",
    url = "\${pg.base-url}",
    configuration = [PgFeignConfig::class],
)
interface PgPaymentFeignClient {

    @PostMapping("/api/v1/payments")
    fun requestPayment(@RequestBody request: PgPaymentRequest): PgResponse<PgPaymentResponse>
}
