package com.loopers.domain.payment

import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "pg-client",
    url = "\${pg.base-url}",
    configuration = [FeignConfig::class],
)
interface PgClient {

    /**
     * 결제 요청
     */
    @PostMapping("/api/v1/payments")
    fun requestPayment(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestBody request: PgDto.PgRequest,
    ): ApiResponse<PgDto.PgTransactionResponse>

    /**
     * 결제 정보 확인
     */
    @GetMapping("/api/v1/payments/{transactionKey}")
    fun getPayment(
        @RequestHeader("X-USER-ID") userId: String,
        @PathVariable transactionKey: String,
    ): ApiResponse<PgDto.PgTransactionDetailResponse>

    /**
     * 주문에 엮인 결제 정보 조회
     */
    @GetMapping("/api/v1/payments")
    fun getPaymentByOrderId(
        @RequestHeader("X-USER-ID") userId: String,
        @RequestParam orderId: String,
    ): ApiResponse<PgDto.PgOrderResponse>
}
