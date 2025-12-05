package com.loopers.infrastructure.payment

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "pg",
    url = "\${pg.base-url}",
    configuration = [PgFeignConfig::class],
)
interface PgFeignClient {

    @PostMapping("/api/v1/payments")
    fun requestPayment(@RequestBody request: PgPaymentRequest): PgResponse<PgPaymentResponse>

    @GetMapping("/api/v1/payments/{transactionKey}")
    fun getPayment(@PathVariable transactionKey: String): PgResponse<PgPaymentDetailResponse>

    @GetMapping("/api/v1/payments")
    fun getPaymentsByOrderId(@RequestParam orderId: String): PgResponse<PgPaymentListResponse>
}
