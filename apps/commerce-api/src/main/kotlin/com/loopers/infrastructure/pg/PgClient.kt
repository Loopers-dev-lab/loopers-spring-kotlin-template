package com.loopers.infrastructure.pg

import com.loopers.interfaces.api.ApiResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "pgClient",
    url = "http://localhost:8082",
    configuration = [PgFeignTimeoutConfig::class],
)
interface PgClient {
    @PostMapping("/api/v1/payments")
    fun payment(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestBody body: PgDto.PaymentRequest,
    ): ApiResponse<PgDto.TransactionResponse>

    @GetMapping("/api/v1/payments/{transactionKey}")
    fun getTransaction(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable transactionKey: String,
    ): ApiResponse<PgDto.TransactionDetailResponse>

    @GetMapping("/api/v1/payments")
    fun getOrder(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestParam("orderId") orderId: String,
    ): ApiResponse<PgDto.OrderResponse>
}
